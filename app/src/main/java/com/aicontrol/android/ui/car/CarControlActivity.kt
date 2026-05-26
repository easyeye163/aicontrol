package com.aicontrol.android.ui.car

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aicontrol.android.R
import com.aicontrol.android.base.BaseActivity
import com.aicontrol.android.floating.voice.TtsManager
import com.aicontrol.android.utils.KVUtils
import com.aicontrol.android.utils.XLog
import com.aicontrol.android.voice.LocalSpeechRecognizer
import com.aicontrol.android.voice.VoiceInputController
import com.aicontrol.android.widget.SingleAxisJoystickView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL

/**
 * 小车控制界面 - 横屏模式
 * 左摇杆控制前后，右摇杆控制左右转向，中间3D停止按钮
 * 支持语音控制：点击语音按钮开启持续识别，自动检测声音停顿并识别，
 * 识别完成后自动继续监听，再次点击按钮关闭持续识别
 */
class CarControlActivity : BaseActivity() {

    companion object {
        private const val TAG = "CarControl"
        private const val PING_INTERVAL_MS = 3000L
        private const val SEND_INTERVAL_MS = 100L
    }

    // 从设置读取的小车地址（onCreate 初始化）
    private var carHost = KVUtils.getCarHost()
    private var carPort = KVUtils.getCarPort()

    private lateinit var ivWifiStatus: ImageView
    private lateinit var tvWifiStatus: TextView
    private lateinit var tvLastCmd: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvDebugLog: TextView
    private lateinit var scrollDebugLog: ScrollView

    /** 调试日志最大行数 */
    private val maxDebugLines = 50
    private val debugLogLines = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val handler = Handler(Looper.getMainLooper())
    private var isConnected = false

    // 摇杆引用
    private lateinit var joystickVertical: SingleAxisJoystickView
    private lateinit var joystickHorizontal: SingleAxisJoystickView

    // 当前摇杆状态
    private var verticalPercent = 0f   // -1(后) ~ 0(中) ~ 1(前)
    private var horizontalPercent = 0f  // -1(左) ~ 0(中) ~ 1(右)

    // 摇杆命令状态追踪（只有推到顶才发送，回中才停止）
    private var verticalCommandActive = false
    private var horizontalCommandActive = false

    // 语音控制
    private var isRecording = false

    private var ttsManager: TtsManager? = null
    private var voiceController: VoiceInputController? = null
    private var localRecognizer: LocalSpeechRecognizer? = null

    // 录音权限请求
    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 权限通过后自动开始录音
            toggleVoice()
        } else {
            Toast.makeText(this, "需要录音权限才能使用语音控制", Toast.LENGTH_LONG).show()
        }
    }

    // 连续发送定时器
    private val sendRunnable = object : Runnable {
        override fun run() {
            sendJoystickCommand()
            handler.postDelayed(this, SEND_INTERVAL_MS)
        }
    }

    // Ping 定时器
    private val pingRunnable = object : Runnable {
        override fun run() {
            checkConnection()
            handler.postDelayed(this, PING_INTERVAL_MS)
        }
    }

    override fun isApplyStatusBarPadding(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_car_control)

        // 从配置读取小车地址
        carHost = KVUtils.getCarHost()
        carPort = KVUtils.getCarPort()

        // 更新界面上的 IP 显示
        findViewById<TextView>(R.id.tvIpAddress)?.text = "$carHost:$carPort"

        initViews()
        initDebugPanel()
        initVoice()
    }

    override fun onResume() {
        super.onResume()
        // 每次恢复时重新读取配置（设置可能已更改）
        carHost = KVUtils.getCarHost()
        carPort = KVUtils.getCarPort()
        findViewById<TextView>(R.id.tvIpAddress)?.text = "$carHost:$carPort"
        handler.post(pingRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pingRunnable)
        handler.removeCallbacks(sendRunnable)
        // 如果正在识别，取消录音
        if (isRecording) {
            voiceController?.destroy()
            voiceController = null
            localRecognizer?.cancel()
            isRecording = false
            updateVoiceButton(false)
            tvLastCmd.text = "就绪"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sendCommand("stop")
        voiceController?.destroy()
        localRecognizer?.destroy()
        ttsManager?.shutdown()
    }

    private fun initViews() {
        ivWifiStatus = findViewById(R.id.ivWifiStatus)
        tvWifiStatus = findViewById(R.id.tvWifiStatus)
        tvLastCmd = findViewById(R.id.tvLastCmd)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvDebugLog = findViewById(R.id.tvDebugLog)
        scrollDebugLog = findViewById(R.id.scrollDebugLog)

        // 设置按钮
        findViewById<View>(R.id.btnSettings)?.setOnClickListener {
            startActivity(Intent(this, CarControlSettingsActivity::class.java))
        }

        // 语音按钮 — 点击开始识别，再点击结束识别
        findViewById<View>(R.id.btnVoice)?.setOnClickListener {
            // 先检查权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return@setOnClickListener
            }
            // 本地语音不需要 API 配置，HTTP 语音需要检查
            if (!KVUtils.isSttUseLocal() && !KVUtils.hasSttConfig()) {
                Toast.makeText(this, "请先配置STT语音识别（设置 > 模型 > STT配置）\n或在STT设置中开启本地语音识别", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            toggleVoice()
        }

        // 左摇杆 — 前后控制 (vertical)
        // 只在推到顶（>=95%）时发送100%方向指令，回中（<=5%）时发送停止
        // 中间过程不发送任何HTTP请求
        joystickVertical = findViewById(R.id.joystickVertical)
        joystickVertical.axis = SingleAxisJoystickView.Axis.VERTICAL
        joystickVertical.onMove = { percent ->
            verticalPercent = percent
            updateSpeedDisplay()
            val absP = Math.abs(percent)
            when {
                absP >= 0.95f && !verticalCommandActive -> {
                    // 推到顶：发送100%方向指令
                    verticalCommandActive = true
                    val direction = if (percent > 0) "forw" else "back"
                    val dirLabel = if (percent > 0) "前进" else "后退"
                    sendCommand(direction, 100)
                    tvLastCmd.text = "$dirLabel 100%"
                    speakTts("已${dirLabel}")
                }
                absP <= 0.05f && verticalCommandActive -> {
                    // 回中：发送停止指令
                    verticalCommandActive = false
                    if (!horizontalCommandActive) {
                        sendCommand("stop")
                        tvLastCmd.text = "已停止"
                        tvSpeed.text = "0%"
                    }
                }
            }
        }
        joystickVertical.onRelease = {
            verticalPercent = 0f
            verticalCommandActive = false
            updateSpeedDisplay()
            if (!horizontalCommandActive) {
                sendCommand("stop")
            }
        }

        // 右摇杆 — 左右转向 (horizontal)
        // 只在推到顶（>=95%）时发送100%方向指令，回中（<=5%）时发送停止
        // 中间过程不发送任何HTTP请求
        joystickHorizontal = findViewById(R.id.joystickHorizontal)
        joystickHorizontal.axis = SingleAxisJoystickView.Axis.HORIZONTAL
        joystickHorizontal.onMove = { percent ->
            horizontalPercent = percent
            updateSpeedDisplay()
            val absP = Math.abs(percent)
            when {
                absP >= 0.95f && !horizontalCommandActive -> {
                    // 推到顶：发送100%方向指令
                    horizontalCommandActive = true
                    val direction = if (percent < 0) "left" else "right"
                    val dirLabel = if (percent < 0) "左转" else "右转"
                    sendCommand(direction, 100)
                    tvLastCmd.text = "$dirLabel 100%"
                    speakTts("已${dirLabel}")
                }
                absP <= 0.05f && horizontalCommandActive -> {
                    // 回中：发送停止指令
                    horizontalCommandActive = false
                    if (!verticalCommandActive) {
                        sendCommand("stop")
                        tvLastCmd.text = "已停止"
                        tvSpeed.text = "0%"
                    }
                }
            }
        }
        joystickHorizontal.onRelease = {
            horizontalPercent = 0f
            horizontalCommandActive = false
            updateSpeedDisplay()
            if (!verticalCommandActive) {
                sendCommand("stop")
            }
        }

        // 中间停止按钮
        val btnStop = findViewById<Button>(R.id.btnStop)
        btnStop.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    executeStop()
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    // ==================== 语音控制（点击开始/结束） ====================

    /**
     * 切换语音识别
     * 点击一次 → 开始识别（识别完自动结束）
     * 再点一次 → 手动停止识别
     */
    private fun toggleVoice() {
        if (isRecording) {
            stopVoice()
        } else {
            startVoice()
        }
    }

    private fun startVoice() {
        updateVoiceButton(true)
        tvLastCmd.text = "语音已开启"
        scrollDebugLog.visibility = View.VISIBLE
        appendDebugLog("[系统] 开始识别")
        XLog.i(TAG, "Voice mode ON")
        // 延迟 300ms 开始，让用户看到按钮变红
        handler.postDelayed({
            if (!isRecording) {
                startListening()
            }
        }, 300)
    }

    private fun stopVoice() {
        isRecording = false
        if (KVUtils.isSttUseLocal()) {
            localRecognizer?.cancel()
        } else {
            voiceController?.destroy()
            voiceController = null
        }
        updateVoiceButton(false)
        tvLastCmd.text = "语音已关闭"
        appendDebugLog("[系统] 手动停止识别")
        XLog.i(TAG, "Voice mode OFF")
    }

    // ==================== 调试日志 ====================

    private fun initDebugPanel() {
        // 长按语音按钮可以切换调试面板显隐
        scrollDebugLog.visibility = View.GONE
    }

    /**
     * 追加一行调试日志到界面上的日志面板
     * 同时输出到 logcat
     */
    private fun appendDebugLog(msg: String) {
        val timeStr = timeFormat.format(Date())
        val line = "[$timeStr] $msg"
        XLog.i(TAG, "[DEBUG] $msg")
        runOnUiThread {
            debugLogLines.add(line)
            if (debugLogLines.size > maxDebugLines) {
                debugLogLines.removeAt(0)
            }
            tvDebugLog.text = debugLogLines.joinToString("\n")
            // 自动滚动到底部
            scrollDebugLog.post {
                scrollDebugLog.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    // ==================== 语音控制（点击开始/结束） ====================

    private fun initVoice() {
        ttsManager = TtsManager(this)
        voiceController = VoiceInputController(this)
        voiceController?.listener = object : VoiceInputController.Listener {
            override fun onListeningStarted() {
                runOnUiThread {
                    isRecording = true
                    updateVoiceButton(true)
                    tvLastCmd.text = "聆听中..."
                }
            }

            override fun onTranscribing() {
                runOnUiThread {
                    isRecording = false
                    tvLastCmd.text = "识别中..."
                }
            }

            override fun onFinalResult(text: String) {
                runOnUiThread {
                    isRecording = false
                    processVoiceCommand(text)
                    // 识别完成，结束
                    updateVoiceButton(false)
                    tvLastCmd.text = "就绪"
                }
            }

            override fun onError(errorCode: Int, message: String) {
                XLog.w(TAG, "STT error: $message")
                runOnUiThread {
                    isRecording = false
                    updateVoiceButton(false)
                    tvLastCmd.text = message
                }
            }
        }
    }

    private fun startListening() {
        if (KVUtils.isSttUseLocal()) {
            startLocalListening()
        } else {
            startHttpListening()
        }
    }

    private fun startLocalListening() {
        // 复用同一个 LocalSpeechRecognizer 实例，避免频繁创建销毁导致识别器忙
        if (localRecognizer == null) {
            val recognizer = LocalSpeechRecognizer(this)
            recognizer.listener = object : LocalSpeechRecognizer.Listener {
                override fun onRecordingStarted() {
                    runOnUiThread {
                        isRecording = true
                        updateVoiceButton(true)
                        tvLastCmd.text = "聆听中(本地)..."
                        appendDebugLog("[识别] 录音开始")
                    }
                }

                override fun onTranscribing() {
                    runOnUiThread {
                        isRecording = false
                        tvLastCmd.text = "识别中..."
                        appendDebugLog("[识别] 录音结束，正在识别...")
                    }
                }

                override fun onResult(text: String) {
                    runOnUiThread {
                        isRecording = false
                        appendDebugLog("[结果] 识别成功: \"$text\"")
                        processVoiceCommand(text)
                        // 识别完成，结束
                        updateVoiceButton(false)
                        tvLastCmd.text = "就绪"
                    }
                }

                override fun onPartialResult(text: String?) {
                    if (text.isNullOrBlank()) return
                    runOnUiThread {
                        tvLastCmd.text = "听: $text"
                        appendDebugLog("[中间] $text")
                    }
                }

                override fun onError(message: String) {
                    XLog.w(TAG, "Local STT error: $message")
                    runOnUiThread {
                        isRecording = false
                        appendDebugLog("[错误] $message")
                        updateVoiceButton(false)
                        tvLastCmd.text = message
                    }
                }
            }
            localRecognizer = recognizer
        }
        localRecognizer?.startListening()
    }

    private fun startHttpListening() {
        voiceController?.destroy()
        val controller = VoiceInputController(this)
        // 持续识别模式：开启自动静音检测，说完话自动停止录音
        controller.setAutoSilenceStop(true)
        // 调试日志回调：让 HTTP STT 内部日志也显示在调试面板
        controller.setDebugLogCallback { msg ->
            appendDebugLog("[远程] $msg")
        }
        controller.listener = object : VoiceInputController.Listener {
            override fun onListeningStarted() {
                runOnUiThread {
                    isRecording = true
                    updateVoiceButton(true)
                    tvLastCmd.text = "聆听中(API)..."
                    appendDebugLog("[识别] 录音开始(API模式)")
                }
            }

            override fun onTranscribing() {
                runOnUiThread {
                    isRecording = false
                    tvLastCmd.text = "识别中..."
                    appendDebugLog("[识别] 录音结束，正在识别...")
                }
            }

            override fun onFinalResult(text: String) {
                runOnUiThread {
                    isRecording = false
                    appendDebugLog("[结果] 识别成功: \"$text\"")
                    processVoiceCommand(text)
                    // 识别完成，结束
                    updateVoiceButton(false)
                    tvLastCmd.text = "就绪"
                }
            }

            override fun onError(errorCode: Int, message: String) {
                XLog.w(TAG, "STT error: $message")
                runOnUiThread {
                    isRecording = false
                    appendDebugLog("[错误] $message")
                    updateVoiceButton(false)
                    tvLastCmd.text = message
                }
            }
        }
        voiceController = controller
        controller.startListening()
    }

    private fun updateVoiceButton(recording: Boolean) {
        val btnVoice = findViewById<View>(R.id.btnVoice)
        if (recording) {
            btnVoice?.setBackgroundColor(Color.parseColor("#ef4444"))
        } else {
            btnVoice?.setBackgroundColor(Color.parseColor("#334155"))
        }
    }

    // ==================== 拼音模糊匹配 ====================

    private fun toPinyin(text: String): String {
        if (text.isEmpty()) return ""
        if (text.all { it.code < 0x4E00 || it.code > 0x9FFF }) return text.lowercase()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            return try {
                android.icu.text.Transliterator.getInstance("Han-Latin").transliterate(text)
                    .replace(Regex("[^a-zA-Z]"), "")
                    .lowercase()
            } catch (e: Exception) {
                XLog.w(TAG, "ICU Transliterator failed: ${e.message}")
                fallbackPinyin(text)
            }
        }
        return fallbackPinyin(text)
    }

    private fun fallbackPinyin(text: String): String {
        val map = mapOf(
            '前' to "qian", '后' to "hou", '左' to "zuo", '右' to "you",
            '停' to "ting", '止' to "zhi", '进' to "jin", '退' to "tui",
            '转' to "zhuan", '走' to "zou", '边' to "bian", '向' to "xiang",
            '开' to "kai", '关' to "guan", '快' to "kuai", '慢' to "man",
            '上' to "shang", '下' to "xia", '起' to "qi", '倒' to "dao",
            '请' to "qing", '往' to "wang", '冲' to "chong", '行' to "xing",
        )
        return text.map { map[it] ?: it.toString() }.joinToString("").lowercase()
    }

    private fun pinyinLevenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
        }
        return dp[m][n]
    }

    private fun pinyinScore(text: String, keyword: String): Float {
        if (text.isEmpty() || keyword.isEmpty()) return 0f
        if (text.contains(keyword)) return 1.0f
        val textPy = toPinyin(text)
        val kwPy = toPinyin(keyword)
        if (textPy.contains(kwPy)) return 1.0f
        val kwPyLen = kwPy.length
        val textPyLen = textPy.length
        var bestScore = 0f
        for (winLen in maxOf(1, kwPyLen - 1)..minOf(textPyLen, kwPyLen + 2)) {
            for (i in 0..textPyLen - winLen) {
                val sub = textPy.substring(i, i + winLen)
                val dist = pinyinLevenshtein(sub, kwPy)
                val maxLen = maxOf(sub.length, kwPy.length)
                val score = 1.0f - dist.toFloat() / maxLen
                if (score > bestScore) bestScore = score
            }
        }
        return bestScore
    }

    /**
     * 处理语音识别结果，用拼音模糊匹配取最高相似度关键词执行
     * 显示：识别原文 → 拼音 → 各关键词得分 → 匹配结果
     */
    private fun processVoiceCommand(text: String) {
        // 标点过滤
        val trimmed = text.trim().replace(Regex("[\\p{P}\\p{S}]"), "")
        XLog.i(TAG, "Voice result: $text -> trimmed: $trimmed")

        val keywords = listOf(
            KVUtils.getCarKeywordForward() to ::executeForward,
            KVUtils.getCarKeywordBackward() to ::executeBackward,
            KVUtils.getCarKeywordLeft() to ::executeLeft,
            KVUtils.getCarKeywordRight() to ::executeRight,
            KVUtils.getCarKeywordStop() to ::executeStop,
        )

        val threshold = 0.5f
        var bestScore = threshold
        var bestAction: (() -> Unit)? = null
        var bestKeyword = ""

        // 计算每个关键词的得分
        val scoreInfo = StringBuilder()
        for ((keyword, action) in keywords) {
            val score = pinyinScore(trimmed, keyword)
            scoreInfo.append("${keyword}=${"%.2f".format(score)} ")
            XLog.i(TAG, "  keyword='$keyword' pinyin='${toPinyin(keyword)}' score=$score")
            if (score > bestScore) {
                bestScore = score
                bestAction = action
                bestKeyword = keyword
            }
        }

        if (bestAction != null) {
            // 匹配成功：显示识别结果和匹配的关键词
            tvLastCmd.text = "\"$trimmed\" -> $bestKeyword(${"%.0f".format(bestScore * 100)}%)"
            appendDebugLog("[匹配] \"$trimmed\" -> $bestKeyword(${"%.0f".format(bestScore * 100)}%)")
            bestAction.invoke()
        } else {
            // 未匹配：显示识别原文和各得分，方便调试
            tvLastCmd.text = "未匹配: \"$trimmed\" [$scoreInfo]"
            appendDebugLog("[未匹配] \"$trimmed\" 得分: $scoreInfo")
            XLog.i(TAG, "No keyword matched. Scores: $scoreInfo")
        }
    }

    // ==================== 指令执行 ====================

    private fun speakTts(text: String) {
        appendDebugLog("[TTS] 播报: \"$text\" (ready=${ttsManager?.isReady})")
        ttsManager?.speak(text)
    }

    private fun executeForward() {
        handler.removeCallbacks(sendRunnable)
        verticalCommandActive = true
        horizontalCommandActive = false
        verticalPercent = 1f
        horizontalPercent = 0f
        joystickVertical.setPercentAnimated(1f, 200L)
        joystickHorizontal.setPercentAnimated(0f, 200L)
        sendCommand("forw", 100)
        tvLastCmd.text = "前进 100%"
        tvSpeed.text = "100%"
        speakTts("已前进")
        handler.postDelayed({ autoReturnToCenter() }, 2000)
    }

    private fun executeBackward() {
        handler.removeCallbacks(sendRunnable)
        verticalCommandActive = true
        horizontalCommandActive = false
        verticalPercent = -1f
        horizontalPercent = 0f
        joystickVertical.setPercentAnimated(-1f, 200L)
        joystickHorizontal.setPercentAnimated(0f, 200L)
        sendCommand("back", 100)
        tvLastCmd.text = "后退 100%"
        tvSpeed.text = "100%"
        speakTts("已后退")
        handler.postDelayed({ autoReturnToCenter() }, 2000)
    }

    private fun executeLeft() {
        handler.removeCallbacks(sendRunnable)
        verticalCommandActive = false
        horizontalCommandActive = true
        verticalPercent = 0f
        horizontalPercent = -1f
        joystickVertical.setPercentAnimated(0f, 200L)
        joystickHorizontal.setPercentAnimated(-1f, 200L)
        sendCommand("left", 100)
        tvLastCmd.text = "左转 100%"
        tvSpeed.text = "100%"
        speakTts("已左转")
        handler.postDelayed({ autoReturnToCenter() }, 2000)
    }

    private fun executeRight() {
        handler.removeCallbacks(sendRunnable)
        verticalCommandActive = false
        horizontalCommandActive = true
        verticalPercent = 0f
        horizontalPercent = 1f
        joystickVertical.setPercentAnimated(0f, 200L)
        joystickHorizontal.setPercentAnimated(1f, 200L)
        sendCommand("right", 100)
        tvLastCmd.text = "右转 100%"
        tvSpeed.text = "100%"
        speakTts("已右转")
        handler.postDelayed({ autoReturnToCenter() }, 2000)
    }

    private fun executeStop() {
        handler.removeCallbacks(sendRunnable)
        verticalCommandActive = false
        horizontalCommandActive = false
        verticalPercent = 0f
        horizontalPercent = 0f
        joystickVertical.setPercentAnimated(0f, 200L)
        joystickHorizontal.setPercentAnimated(0f, 200L)
        sendCommand("stop")
        tvLastCmd.text = "已停止"
        tvSpeed.text = "0%"
        speakTts("已停止")
    }

    private fun autoReturnToCenter() {
        verticalCommandActive = false
        horizontalCommandActive = false
        verticalPercent = 0f
        horizontalPercent = 0f
        joystickVertical.setPercentAnimated(0f, 200L)
        joystickHorizontal.setPercentAnimated(0f, 200L)
        sendCommand("stop")
        tvLastCmd.text = "就绪"
        tvSpeed.text = "0%"
    }

    // ==================== 手动控制逻辑 ====================

    private fun sendJoystickCommand() {
        val absV = Math.abs(verticalPercent)
        val absH = Math.abs(horizontalPercent)

        if (absV <= 0.05f && absH <= 0.05f) {
            handler.removeCallbacks(sendRunnable)
            return
        }

        if (absV >= absH) {
            val speed = (absV * 90 + 10).toInt()
            val direction = if (verticalPercent > 0) "forw" else "back"
            sendCommand("$direction", speed)
            val dirLabel = if (verticalPercent > 0) "前进" else "后退"
            tvLastCmd.text = "$dirLabel $speed%"
        } else {
            val speed = (absH * 90 + 10).toInt()
            val direction = if (horizontalPercent < 0) "left" else "right"
            sendCommand("$direction", speed)
            val dirLabel = if (horizontalPercent < 0) "左转" else "右转"
            tvLastCmd.text = "$dirLabel $speed%"
        }
    }

    private fun updateSpeedDisplay() {
        val absV = Math.abs(verticalPercent)
        val absH = Math.abs(horizontalPercent)
        val maxVal = Math.max(absV, absH)
        if (maxVal <= 0.05f) {
            tvSpeed.text = "0%"
        } else {
            val displayPercent = ((maxVal) * 90 + 10).toInt()
            tvSpeed.text = "$displayPercent%"
        }
    }

    // ==================== 网络通信 ====================

    private fun sendCommand(direction: String, speed: Int = 50) {
        val urlStr = "http://$carHost:$carPort/control/${direction}_$speed"
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = URL(urlStr)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    conn.requestMethod = "GET"
                    conn.responseCode
                    conn.disconnect()
                }
            } catch (_: Exception) {}
        }
    }

    private fun checkConnection() {
        lifecycleScope.launch {
            val reachable = withContext(Dispatchers.IO) {
                try {
                    val address = InetAddress.getByName(carHost)
                    address.isReachable(1500)
                } catch (_: Exception) { false }
            }
            updateConnectionStatus(reachable)
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
        isConnected = connected
        if (connected) {
            ivWifiStatus.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4ade80"))
            tvWifiStatus.text = "已连接"
            tvWifiStatus.setTextColor(Color.parseColor("#4ade80"))
        } else {
            ivWifiStatus.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#666666"))
            tvWifiStatus.text = "未连接"
            tvWifiStatus.setTextColor(Color.parseColor("#888888"))
        }
    }
}
