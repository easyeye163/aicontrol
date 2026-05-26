package com.aicontrol.android.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Android 系统自带语音识别器
 *
 * 封装 SpeechRecognizer，使用持续识别模式。
 * 无需网络 API，可离线使用（取决于系统是否下载了离线语音包）。
 *
 * 关键设计：
 * - 复用同一个 SpeechRecognizer 实例，避免频繁 create/destroy 导致 ERROR_RECOGNIZER_BUSY
 * - isListening 状态锁，防止并发调用
 * - ERROR_RECOGNIZER_BUSY / ERROR_CLIENT 时自动延迟重试（最多 1 次）
 * - 移除 EXTRA_PROMPT（避免部分 ROM 弹窗干扰）
 * - 设置 EXTRA_CALLING_PACKAGE（部分国产 ROM 要求）
 * - 支持中间结果（onPartialResult）用于实时显示
 */
class LocalSpeechRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "LocalSTT"
        private const val RETRY_DELAY_MS = 300L
        private const val MAX_RETRY = 1
        /** cancel() 后等待多久再 startListening()，某些设备需要延时才能正常重启 */
        private const val CANCEL_TO_START_DELAY_MS = 200L
        /** 识别超时：如果开始录音后 N 秒内没有任何结果/错误回调，强制重启 */
        private const val RECOGNITION_TIMEOUT_MS = 12000L
    }

    interface Listener {
        /** 语音识别开始（录音中） */
        fun onRecordingStarted() {}
        /** 录音结束，正在识别中 */
        fun onTranscribing() {}
        /** 识别成功 */
        fun onResult(text: String) {}
        /** 中间结果（实时显示用，可能为空取决于设备） */
        fun onPartialResult(text: String?) {}
        /** 发生错误 */
        fun onError(message: String) {}
    }

    var listener: Listener? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    /** 识别超时 Runnable，防止识别器挂起导致持续识别卡死 */
    private var recognitionTimeoutRunnable: Runnable? = null

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.i(TAG, "Beginning of speech")
            isListening = true
            listener?.onRecordingStarted()
            // 启动识别超时计时器
            cancelRecognitionTimeout()
            recognitionTimeoutRunnable = Runnable {
                Log.w(TAG, "Recognition timeout after ${RECOGNITION_TIMEOUT_MS}ms, forcing restart")
                isListening = false
                // 超时当作错误处理，通知上层重启
                listener?.onError("识别超时(${RECOGNITION_TIMEOUT_MS / 1000}秒无响应)")
            }
            handler.postDelayed(recognitionTimeoutRunnable!!, RECOGNITION_TIMEOUT_MS)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // 音量变化，可用于显示录音状态
            // 仅在音量较大时记录（避免刷屏）
            if (rmsdB > 2.0f) {
                Log.d(TAG, "RMS: %.1f".format(rmsdB))
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.i(TAG, "End of speech")
            isListening = false
            cancelRecognitionTimeout()
            listener?.onTranscribing()
        }

        override fun onError(error: Int) {
            isListening = false
            cancelRecognitionTimeout()

            val msg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "录音错误"
                SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "录音权限不足"
                SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音输入"
                else -> "未知错误($error)"
            }
            Log.w(TAG, "SpeechRecognizer error $error: $msg")

            // 识别器忙或客户端错误 → 延迟重试一次
            if ((error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                        || error == SpeechRecognizer.ERROR_CLIENT)
                && retryCount < MAX_RETRY
            ) {
                retryCount++
                Log.i(TAG, "Retrying startListening (retry=$retryCount)...")
                handler.postDelayed({
                    internalStartListening()
                }, RETRY_DELAY_MS)
                return
            }

            // 重试次数耗尽或其他错误，通知上层
            retryCount = 0
            listener?.onError(msg)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            retryCount = 0
            cancelRecognitionTimeout()

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            // 打印完整的 results Bundle 用于调试
            Log.i(TAG, "onResults called. bundle=$results")
            Log.i(TAG, "onResults matches=$matches")
            if (matches.isNullOrEmpty()) {
                Log.w(TAG, "onResults: matches is null or empty!")
                listener?.onError("未识别到语音内容")
                return
            }
            val text = matches[0]
            Log.i(TAG, "Result: $text (all: $matches)")
            listener?.onResult(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            Log.d(TAG, "onPartialResults called. bundle=$partialResults")
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Log.d(TAG, "Partial: $text")
                listener?.onPartialResult(text)
            } else {
                Log.d(TAG, "onPartialResults: no matches")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * 开始语音识别（对外接口）
     *
     * 关键：cancel() 后必须延时 200ms 再 startListening()，
     * 否则某些设备/ROM 上识别器会静默无响应（onBeginningOfSpeech 正常回调，
     * 但之后 onEndOfSpeech/onResults/onError 全部不回调）
     */
    fun startListening() {
        // 先检查设备是否支持语音识别
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener?.onError("设备不支持语音识别")
            return
        }

        // 确保 SpeechRecognizer 实例已创建（复用，不每次新建）
        ensureRecognizer()

        val needDelay = speechRecognizer != null // 非首次启动需要 cancel+延时

        // cancel 旧会话
        try {
            speechRecognizer?.cancel()
            Log.i(TAG, "Pre-cancel before startListening (was isListening=$isListening)")
        } catch (e: Exception) {
            Log.w(TAG, "pre-cancel error", e)
        }
        isListening = false
        retryCount = 0

        if (needDelay) {
            // 非首次启动：cancel 后延时再 start，避免识别器静默无响应
            Log.i(TAG, "Delaying ${CANCEL_TO_START_DELAY_MS}ms after cancel before start")
            handler.postDelayed({
                if (!isListening) {
                    internalStartListening()
                }
            }, CANCEL_TO_START_DELAY_MS)
        } else {
            // 首次启动：无需延时
            internalStartListening()
        }
    }

    /**
     * 内部实际启动监听
     */
    private fun internalStartListening() {
        val recognizer = speechRecognizer ?: run {
            listener?.onError("语音识别器初始化失败")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // 不设置 EXTRA_PROMPT，避免部分 ROM 弹出对话框干扰
            // 设置语音输入结束的静音检测时长（毫秒），用于持续识别
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 200L)
        }

        try {
            recognizer.startListening(intent)
            Log.i(TAG, "startListening called")
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            retryCount = 0
            listener?.onError("启动语音识别失败: ${e.message}")
        }
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        if (!isListening) return
        try {
            speechRecognizer?.stopListening()
            Log.i(TAG, "stopListening called")
        } catch (e: Exception) {
            Log.w(TAG, "stopListening error", e)
            isListening = false
        }
    }

    /**
     * 取消语音识别（不触发 onResults）
     */
    fun cancel() {
        cancelRecognitionTimeout()
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "cancel error", e)
        }
        isListening = false
        retryCount = 0
    }

    /**
     * 取消识别超时计时器
     */
    private fun cancelRecognitionTimeout() {
        recognitionTimeoutRunnable?.let {
            handler.removeCallbacks(it)
        }
        recognitionTimeoutRunnable = null
    }

    /**
     * 确保 SpeechRecognizer 实例存在（复用模式）
     */
    private fun ensureRecognizer() {
        if (speechRecognizer != null) return
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
            Log.i(TAG, "SpeechRecognizer created (reusable)")
        } catch (e: Exception) {
            Log.e(TAG, "createSpeechRecognizer failed", e)
            listener?.onError("创建语音识别器失败: ${e.message}")
            speechRecognizer = null
        }
    }

    /**
     * 释放资源（Activity onDestroy 时调用）
     */
    fun destroy() {
        cancelRecognitionTimeout()
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroy error", e)
        }
        speechRecognizer = null
        isListening = false
        retryCount = 0
    }
}
