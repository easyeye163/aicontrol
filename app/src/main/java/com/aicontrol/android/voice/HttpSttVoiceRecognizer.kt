package com.aicontrol.android.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aicontrol.android.utils.KVUtils
import com.aicontrol.android.utils.XLog
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * HTTP STT 语音识别器（增强版 VAD）
 *
 * 使用 AudioRecord 录制 PCM 音频，通过 HTTP 发送到 OpenAI 兼容的
 * /v1/audio/transcriptions 接口进行语音转文字。
 *
 * 完全不依赖 Android SpeechRecognizer，兼容国产 ROM（OPPO、vivo 等）。
 *
 * 支持两种工作模式：
 * 1. 手动模式（默认）：调用 startRecording() 开始，stopRecording() 停止并识别
 * 2. 自动模式（setAutoSilenceStop(true)）：自动检测静音，静音超时后自动停止录音并识别
 *
 * v0.0.81 增强 VAD（Voice Activity Detection）：
 * - 环境噪声自动校准：利用前 600ms 采集环境底噪，动态计算静音阈值
 * - 滚动平均 RMS：使用指数移动平均（EMA）平滑音量波动，避免瞬间安静误触发
 * - 语音优先策略：必须先检测到有效语音（RMS 超过阈值），才允许后续静音触发停止
 * - 最短语音时长保护：有效语音不足 300ms 不允许停止（防止呼吸/噪声误判为语音）
 * - 两段式静音检测：短静音（< 800ms）→ 饭后/句子间停顿，容忍；长静音（≥ 2s）→ 真正结束
 * - 调试日志回调：支持外部传入日志回调，方便界面显示
 */
class HttpSttVoiceRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "HttpSttVoiceRecognizer"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val TIMEOUT_SECONDS = 30L
        private const val MIN_RECORDING_BYTES = 3200  // 16kHz * 2bytes * 0.1s = 最短有效录音

        /** 默认 STT 模型 */
        const val DEFAULT_STT_MODEL = "whisper-1"

        // ---- 增强型 VAD 参数 ----
        /** 静音检测检查间隔（毫秒） */
        private const val VAD_CHECK_INTERVAL_MS = 200L
        /** 环境噪声校准期（毫秒），此期间采集底噪 */
        private const val CALIBRATION_DURATION_MS = 600L
        /** 静音阈值 = 环境底噪 RMS × 此倍数（至少 200） */
        private const val NOISE_MULTIPLIER = 2.5
        /** 静音阈值绝对下限（即使环境很安静也不会低于此值） */
        private const val MIN_SILENCE_THRESHOLD = 200
        /** 静音阈值绝对上限（环境很嘈杂时不会超过此值） */
        private const val MAX_SILENCE_THRESHOLD = 2000
        /** EMA 平滑系数，0~1，越小越平滑。0.3 = 70% 历史 + 30% 当前 */
        private const val EMA_ALPHA = 0.3f
        /** 持续静音多久（毫秒）后判定语音结束，自动停止录音 */
        private const val SILENCE_DURATION_MS = 2000L
        /** 最短有效语音时长（毫秒），不足此时长不触发停止（防止噪声被误判为语音） */
        private const val MIN_SPEECH_DURATION_MS = 300L
        /** 最长录音时间（毫秒），防止无限录音 */
        private const val MAX_RECORDING_DURATION_MS = 15000L
        /** 开头忽略时间（毫秒），刚开录音时可能有噪声，不计入静音（同时用于噪声校准） */
        private const val LEAD_IN_IGNORE_MS = CALIBRATION_DURATION_MS + 100L

        /**
         * 创建 OkHttp 客户端，可选绑定到特定网络
         * @param network 如果指定，所有请求走该网络（用于蜂窝网络回退）
         */
        private fun createHttpClient(network: Network?): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (network != null) {
                builder.socketFactory(network.socketFactory)
            }
            return builder.build()
        }
    }

    interface Listener {
        /** 录音开始 */
        fun onRecordingStarted() {}
        /** 录音结束，正在识别中 */
        fun onTranscribing() {}
        /** 识别成功 */
        fun onResult(text: String) {}
        /** 发生错误 */
        fun onError(message: String) {}
    }

    var listener: Listener? = null
    var sttModel: String = DEFAULT_STT_MODEL

    /** 调试日志回调（可选），用于将内部日志输出到界面 */
    var debugLogCallback: ((String) -> Unit)? = null

    /** 是否开启自动静音停止（持续识别模式需要开启） */
    private var autoSilenceStop = false

    private val isRecording = AtomicBoolean(false)
    private var httpClient = createHttpClient(null)
    private var mobileNetwork: Network? = null
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val recordingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pcmBuffer: ByteArrayOutputStream? = null

    // ---- 增强型 VAD 状态 ----
    private val mainHandler = Handler(Looper.getMainLooper())
    /** 录音开始时间 */
    private var recordingStartTime = 0L
    /** 当前动态静音阈值（校准后确定） */
    private var dynamicSilenceThreshold = MIN_SILENCE_THRESHOLD
    /** 是否完成环境噪声校准 */
    private var isCalibrated = false
    /** 校准期间累积的 RMS 采样值（用于算平均底噪） */
    private val calibrationSamples = mutableListOf<Int>()
    /** EMA 平滑后的 RMS 值 */
    @Volatile
    private var smoothedRms = 0
    /** 原始瞬时 RMS（最近一次计算的值） */
    @Volatile
    private var rawRms = 0
    /** 是否已检测到有效语音（在允许静音停止前必须先有语音） */
    private var hasDetectedSpeech = false
    /** 首次检测到语音的时间（用于计算有效语音时长） */
    private var firstSpeechTime = 0L
    /** 最后一次检测到语音的时间（用于判断语音是否已结束） */
    private var lastSpeechTime = 0L
    /** 累计有效语音时长（毫秒） */
    private var totalSpeechDuration = 0L
    /** 静音开始时间（第一次 RMS 低于阈值的时间），0 表示正在说话 */
    private var silenceStartTime = 0L
    /** 是否已经通过自动静音触发过停止（防止重复触发） */
    private var autoStopTriggered = false
    /** 静音检测定时 Runnable */
    private var vadCheckRunnable: Runnable? = null
    /** 录音超时 Runnable */
    private var recordingTimeoutRunnable: Runnable? = null

    val recording: Boolean
        get() = isRecording.get()

    /**
     * 设置是否开启自动静音停止
     * 开启后，录音时如果持续静音超过 SILENCE_DURATION_MS，自动停止录音并识别
     * 持续识别模式下必须开启
     */
    fun setAutoSilenceStop(enabled: Boolean) {
        autoSilenceStop = enabled
        logDebug("自动VAD: ${if (enabled) "开启" else "关闭"}")
    }

    /**
     * 开始录音
     */
    fun startRecording() {
        if (isRecording.get()) {
            XLog.w(TAG, "Already recording")
            return
        }

        // 检查网络：WiFi无互联网时尝试使用移动流量
        ensureNetworkAvailable()

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            listener?.onError("音频参数不支持")
            XLog.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        // 重置 VAD 状态
        recordingStartTime = System.currentTimeMillis()
        isCalibrated = false
        dynamicSilenceThreshold = MIN_SILENCE_THRESHOLD
        smoothedRms = 0
        rawRms = 0
        hasDetectedSpeech = false
        firstSpeechTime = 0L
        lastSpeechTime = 0L
        totalSpeechDuration = 0L
        silenceStartTime = 0L
        autoStopTriggered = false
        calibrationSamples.clear()

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                listener?.onError("录音器初始化失败")
                XLog.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            pcmBuffer = ByteArrayOutputStream()
            audioRecord?.startRecording()
            isRecording.set(true)
            listener?.onRecordingStarted()
            logDebug("录音开始 (自动VAD=${autoSilenceStop})")
            XLog.i(TAG, "Recording started (PCM 16kHz mono)")

            recordingJob = recordingScope.launch {
                val buffer = ByteArray(bufferSize)
                while (isActive && isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        pcmBuffer?.write(buffer, 0, read)
                        // 计算 RMS 并更新 EMA
                        if (autoSilenceStop) {
                            rawRms = calculateRms(buffer, read)
                            smoothedRms = if (smoothedRms == 0) {
                                rawRms
                            } else {
                                // 指数移动平均：smoothed = α * current + (1-α) * smoothed
                                (EMA_ALPHA * rawRms + (1 - EMA_ALPHA) * smoothedRms).toInt()
                            }
                        }
                    }
                }
            }

            // 启动 VAD
            if (autoSilenceStop) {
                startVad()
                startRecordingTimeout()
            }
        } catch (e: SecurityException) {
            isRecording.set(false)
            listener?.onError("录音权限不足")
            XLog.e(TAG, "SecurityException: ${e.message}")
        } catch (e: Exception) {
            isRecording.set(false)
            listener?.onError("录音启动失败: ${e.message}")
            XLog.e(TAG, "Failed to start recording", e)
        }
    }

    /**
     * 停止录音并发送音频进行 STT 识别
     */
    fun stopRecording() {
        if (!isRecording.get()) return
        isRecording.set(false)

        // 停止 VAD 和录音超时
        stopVad()
        stopRecordingTimeout()

        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            XLog.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null
        val duration = System.currentTimeMillis() - recordingStartTime
        logDebug("录音停止 (${duration}ms, 语音${totalSpeechDuration}ms, 阈值=${dynamicSilenceThreshold})")
        XLog.i(TAG, "Recording stopped, duration=${duration}ms, speech=${totalSpeechDuration}ms")

        val pcmData = pcmBuffer?.toByteArray()
        pcmBuffer = null

        if (pcmData == null || pcmData.size < MIN_RECORDING_BYTES) {
            val size = pcmData?.size ?: 0
            listener?.onError("录音太短，请长按说话")
            logDebug("录音太短: ${size} bytes")
            XLog.w(TAG, "Recording too short: ${size} bytes")
            return
        }

        listener?.onTranscribing()
        logDebug("正在发送识别...")
        recordingScope.launch {
            transcribeAudio(pcmData)
        }
    }

    // ==================== 增强型 VAD ====================

    /**
     * 启动语音活动检测（VAD）
     *
     * 流程：
     * 1. 校准期（0~600ms）：采集环境底噪，计算动态阈值
     * 2. 等待语音：校准完成后，等待 RMS 超过阈值（检测到有效语音）
     * 3. 监听结束：检测到语音后，持续监听。如果 RMS 低于阈值：
     *    a. 短静音（< 2s）：可能是句子间停顿，容忍
     *    b. 长静音（≥ 2s）且有效语音 ≥ 300ms：判定语音结束，自动停止录音
     */
    private fun startVad() {
        stopVad()
        vadCheckRunnable = object : Runnable {
            override fun run() {
                if (!isRecording.get() || autoStopTriggered) return

                val now = System.currentTimeMillis()
                val elapsed = now - recordingStartTime

                // ========== 阶段 1：环境噪声校准 ==========
                if (!isCalibrated) {
                    // 在校准期内，收集 RMS 采样用于计算底噪
                    if (rawRms > 0) {
                        calibrationSamples.add(rawRms)
                    }
                    if (elapsed >= CALIBRATION_DURATION_MS) {
                        // 校准完成，计算动态阈值
                        finishCalibration()
                    }
                    // 校准期间不检测语音，继续
                    mainHandler.postDelayed(this, VAD_CHECK_INTERVAL_MS)
                    return
                }

                // ========== 阶段 2：等待语音 ==========
                val isSpeech = smoothedRms >= dynamicSilenceThreshold

                if (!hasDetectedSpeech) {
                    // 还没检测到语音
                    if (isSpeech) {
                        // 首次检测到语音
                        hasDetectedSpeech = true
                        firstSpeechTime = now
                        lastSpeechTime = now
                        logDebug("检测到语音开始 (RMS=$smoothedRms, 阈值=$dynamicSilenceThreshold)")
                    }
                    // 未检测到语音时，不计时静音，也不触发停止
                    mainHandler.postDelayed(this, VAD_CHECK_INTERVAL_MS)
                    return
                }

                // ========== 阶段 3：监听语音结束 ==========
                if (isSpeech) {
                    // 仍在说话
                    val speechLen = now - lastSpeechTime
                    // 如果之前在静音，现在又检测到声音 → 累积语音时长
                    if (silenceStartTime > 0) {
                        // 把之前的静音间隔之前的语音时长累积
                        // silenceStartTime 之前的 lastSpeechTime 记录了上一次说话时间
                        totalSpeechDuration += (silenceStartTime - lastSpeechTime)
                        silenceStartTime = 0L
                        logDebug("语音继续 (RMS=$smoothedRms)")
                    } else {
                        totalSpeechDuration += (now - lastSpeechTime)
                    }
                    lastSpeechTime = now
                } else {
                    // 当前静音
                    if (silenceStartTime == 0L) {
                        // 刚进入静音
                        silenceStartTime = now
                        // 偶尔输出调试日志（不要每个周期都打）
                        if ((now - recordingStartTime) % 2000 < VAD_CHECK_INTERVAL_MS) {
                            logDebug("短暂静音中... (RMS=$smoothedRms)")
                        }
                    } else {
                        val silenceDuration = now - silenceStartTime
                        if (silenceDuration >= SILENCE_DURATION_MS) {
                            // 计算最终语音时长
                            val finalSpeechDuration = totalSpeechDuration + (silenceStartTime - firstSpeechTime)

                            if (finalSpeechDuration < MIN_SPEECH_DURATION_MS) {
                                // 有效语音太短，不算真正的语音，重置继续等
                                logDebug("语音太短(${finalSpeechDuration}ms < ${MIN_SPEECH_DURATION_MS}ms)，重置继续等待")
                                hasDetectedSpeech = false
                                firstSpeechTime = 0L
                                lastSpeechTime = 0L
                                totalSpeechDuration = 0L
                                silenceStartTime = 0L
                                mainHandler.postDelayed(this, VAD_CHECK_INTERVAL_MS)
                                return
                            }

                            // 持续静音超时且语音足够长 → 自动停止录音
                            logDebug("语音结束: 静音${silenceDuration}ms, 语音${finalSpeechDuration}ms, 自动停止")
                            XLog.i(TAG, "Auto-stop: silence=${silenceDuration}ms, speech=${finalSpeechDuration}ms")
                            autoStopTriggered = true
                            stopRecording()
                            return
                        }
                    }
                }

                // 继续检查
                mainHandler.postDelayed(this, VAD_CHECK_INTERVAL_MS)
            }
        }
        mainHandler.postDelayed(vadCheckRunnable!!, VAD_CHECK_INTERVAL_MS)
    }

    /**
     * 完成环境噪声校准，计算动态静音阈值
     */
    private fun finishCalibration() {
        isCalibrated = true
        if (calibrationSamples.isEmpty()) {
            dynamicSilenceThreshold = MIN_SILENCE_THRESHOLD
            logDebug("校准完成(无采样)，使用默认阈值=$dynamicSilenceThreshold")
            return
        }
        // 取中位数作为底噪（比平均值更抗干扰）
        val sorted = calibrationSamples.sorted()
        val medianNoise = sorted[sorted.size / 2]
        // 阈值 = 底噪 × 倍数，限制在合理范围
        dynamicSilenceThreshold = (medianNoise * NOISE_MULTIPLIER).toInt()
            .coerceIn(MIN_SILENCE_THRESHOLD, MAX_SILENCE_THRESHOLD)
        calibrationSamples.clear()
        logDebug("校准完成: 底噪=${medianNoise}, 阈值=$dynamicSilenceThreshold")
        XLog.i(TAG, "VAD calibrated: noise=$medianNoise, threshold=$dynamicSilenceThreshold")
    }

    /**
     * 停止 VAD 检测定时器
     */
    private fun stopVad() {
        vadCheckRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        vadCheckRunnable = null
    }

    /**
     * 启动录音超时保护
     * 如果录音超过 MAX_RECORDING_DURATION_MS 仍未自动停止，强制停止
     */
    private fun startRecordingTimeout() {
        stopRecordingTimeout()
        recordingTimeoutRunnable = Runnable {
            if (!isRecording.get() || autoStopTriggered) return@Runnable
            logDebug("录音超时 ${MAX_RECORDING_DURATION_MS / 1000}s，强制停止")
            XLog.w(TAG, "Recording timeout: ${MAX_RECORDING_DURATION_MS / 1000}s")
            autoStopTriggered = true
            stopRecording()
        }
        mainHandler.postDelayed(recordingTimeoutRunnable!!, MAX_RECORDING_DURATION_MS)
    }

    /**
     * 停止录音超时定时器
     */
    private fun stopRecordingTimeout() {
        recordingTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        recordingTimeoutRunnable = null
    }

    /**
     * 计算 PCM 16-bit 音频数据的 RMS（均方根）
     * 返回值范围约 0~32767，安静环境约 50~300，说话约 500~5000+
     */
    private fun calculateRms(buffer: ByteArray, length: Int): Int {
        var sum = 0L
        val sampleCount = length / 2
        for (i in 0 until sampleCount) {
            val index = i * 2
            val sample = (buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xFF)
            sum += sample.toLong() * sample.toLong()
        }
        if (sampleCount == 0) return 0
        return sqrt(sum.toDouble() / sampleCount).toInt()
    }

    // ==================== 调试日志 ====================

    private fun logDebug(msg: String) {
        XLog.i(TAG, msg)
        debugLogCallback?.invoke(msg)
    }

    // ==================== 网络和 STT ====================

    /**
     * 将 PCM 数据转为 WAV 并发送到 STT API
     */
    private suspend fun transcribeAudio(pcmData: ByteArray) = withContext(Dispatchers.IO) {
        try {
            val wavData = pcmToWav(pcmData, SAMPLE_RATE, 1, 16)
            logDebug("WAV ${wavData.size} bytes, 发送到 STT...")

            val baseUrl = KVUtils.getSttBaseUrl().trimEnd('/')
            val apiKey = KVUtils.getSttApiKey()
            val sttModelFromConfig = KVUtils.getSttModel()
            if (sttModelFromConfig.isNotEmpty()) {
                sttModel = sttModelFromConfig
            }

            XLog.i(TAG, "STT config: baseUrl=$baseUrl, apiKey=${if (apiKey.isNotEmpty()) "***" else "(empty)"}, model=$sttModel")

            if (baseUrl.isEmpty()) {
                listener?.onError("请先配置 STT（设置 > 模型 > STT 配置）")
                return@withContext
            }

            val sttUrl = when {
                baseUrl.endsWith("/audio/transcriptions") -> baseUrl
                baseUrl.contains("/audio/transcriptions") -> baseUrl
                baseUrl.endsWith("/v1") -> "$baseUrl/audio/transcriptions"
                baseUrl.contains("/v1/") -> "${baseUrl}audio/transcriptions"
                else -> "$baseUrl/v1/audio/transcriptions"
            }

            logDebug("STT URL: $sttUrl")
            XLog.i(TAG, "STT request URL: $sttUrl")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", sttModel)
                .addFormDataPart("language", "zh")
                .addFormDataPart(
                    "file",
                    "recording.wav",
                    wavData.toRequestBody("audio/wav".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(sttUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    XLog.e(TAG, "STT HTTP ${response.code}: $body")
                    logDebug("STT 失败: HTTP ${response.code}")
                    listener?.onError("语音识别失败(HTTP ${response.code})")
                    return@withContext
                }

                val json = JSONObject(body)
                val text = json.optString("text", "").trim()

                if (text.isEmpty()) {
                    listener?.onError("未识别到语音内容")
                    logDebug("STT 返回空结果")
                    XLog.w(TAG, "STT empty result: $body")
                } else {
                    logDebug("识别结果: \"$text\"")
                    XLog.i(TAG, "STT result: $text")
                    listener?.onResult(text)
                }
            }
        } catch (e: Exception) {
            XLog.e(TAG, "transcribeAudio failed", e)
            logDebug("识别异常: ${e.message}")
            listener?.onError("语音识别失败: ${e.message}")
        }
    }

    /**
     * 检查当前网络是否有互联网，如果没有（WiFi连小车无外网），尝试切换到移动流量
     */
    private fun ensureNetworkAvailable() {
        try {
            val cm = connectivityManager ?: return
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)

            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

            if (hasInternet) {
                if (mobileNetwork != null) {
                    logDebug("默认网络可用，切换回默认网络")
                    XLog.i(TAG, "Default network has internet, switching back to default")
                    mobileNetwork = null
                    httpClient = createHttpClient(null)
                }
                return
            }

            logDebug("当前网络无互联网，查找蜂窝网络...")
            XLog.i(TAG, "Current network has no internet, looking for cellular...")

            val networks = cm.allNetworks
            for (network in networks) {
                val netCaps = cm.getNetworkCapabilities(network) ?: continue
                if (netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    && netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ) {
                    mobileNetwork = network
                    httpClient = createHttpClient(network)
                    logDebug("已切换到蜂窝网络")
                    XLog.i(TAG, "Using cellular network for STT API: $network")
                    return
                }
            }
            logDebug("未找到可用的蜂窝网络")
            XLog.w(TAG, "No cellular network available for fallback")
        } catch (e: Exception) {
            XLog.w(TAG, "ensureNetworkAvailable error: ${e.message}")
        }
    }

    /**
     * 释放所有资源
     */
    fun destroy() {
        isRecording.set(false)
        stopVad()
        stopRecordingTimeout()
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        pcmBuffer = null
        recordingScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        listener = null
        debugLogCallback = null
    }

    /**
     * 将 PCM 裸数据封装为标准 WAV 格式
     */
    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray())

        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        buffer.put(pcmData)

        return buffer.array()
    }
}
