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
import android.widget.TextView
import android.widget.Toast
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
 * 支持语音控制：按住语音按钮说话，松开后自动识别关键词控制+TTS播报
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

    private val handler = Handler(Looper.getMainLooper())
    private var isConnected = false

    // 摇杆引用
    private lateinit var joystickVertical: SingleAxisJoystickView
    private lateinit var joystickHorizontal: SingleAxisJoystickView

    // 当前摇杆状态
    private var verticalPercent = 0f   // -1(后) ~ 0(中) ~ 1(前)
    private var horizontalPercent = 0f  // -1(左) ~ 0(中) ~ 1(右)

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
            startRecording()
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
        // 如果正在录音，取消录音（不销毁实例，onResume 可复用）
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

        // 设置按钮
        findViewById<View>(R.id.btnSettings)?.setOnClickListener {
            startActivity(Intent(this, CarControlSettingsActivity::class.java))
        }

        // 语音按钮 — 按住说话，松开停止
        findViewById<View>(R.id.btnVoice)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 先检查权限
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@setOnTouchListener true
                    }
                    // 本地语音不需要 API 配置，HTTP 语音需要检查
                    if (!KVUtils.isSttUseLocal() && !KVUtils.hasSttConfig()) {
                        Toast.makeText(this, "请先配置STT语音识别（设置 > 模型 > STT配置）\n或在STT设置中开启本地语音识别", Toast.LENGTH_LONG).show()
                        return@setOnTouchListener true
                    }
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    true
                }
                else -> false
            }
        }

        // 左摇杆 — 前后控制 (vertical)
        joystickVertical = findViewById(R.id.joystickVertical)
        joystickVertical.axis = SingleAxisJoystickView.Axis.VERTICAL
        joystickVertical.onMove = { percent ->
            verticalPercent = percent
            updateSpeedDisplay()
            handler.removeCallbacks(sendRunnable)
            if (Math.abs(verticalPercent) > 0.05f || Math.abs(horizontalPercent) > 0.05f) {
                handler.post(sendRunnable)
            } else if (Math.abs(horizontalPercent) <= 0.05f) {
                sendCommand("stop")
            }
        }
        joystickVertical.onRelease = {
            verticalPercent = 0f
            updateSpeedDisplay()
            if (Math.abs(horizontalPercent) <= 0.05f) {
                handler.removeCallbacks(sendRunnable)
                sendCommand("stop")
            }
        }

        // 右摇杆 — 左右转向 (horizontal)
        joystickHorizontal = findViewById(R.id.joystickHorizontal)
        joystickHorizontal.axis = SingleAxisJoystickView.Axis.HORIZONTAL
        joystickHorizontal.onMove = { percent ->
            horizontalPercent = percent
            updateSpeedDisplay()
            handler.removeCallbacks(sendRunnable)
            if (Math.abs(verticalPercent) > 0.05f || Math.abs(horizontalPercent) > 0.05f) {
                handler.post(sendRunnable)
            } else if (Math.abs(verticalPercent) <= 0.05f) {
                sendCommand("stop")
            }
        }
        joystickHorizontal.onRelease = {
            horizontalPercent = 0f
            updateSpeedDisplay()
            if (Math.abs(verticalPercent) <= 0.05f) {
                handler.removeCallbacks(sendRunnable)
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

    // ==================== 语音控制（按住说话） ====================

    private fun initVoice() {
        ttsManager = TtsManager(this)
        voiceController = VoiceInputController(this)
        voiceController?.listener = object : VoiceInputController.Listener {
            override fun onListeningStarted() {
                runOnUiThread { tvLastCmd.text = "聆听中..." }
            }

            override fun onTranscribing() {
                runOnUiThread {
                    isRecording = false
                    updateVoiceButton(false)
                    tvLastCmd.text = "识别中..."
                }
            }

            override fun onFinalResult(text: String) {
                runOnUiThread { processVoiceCommand(text) }
            }

            override fun onError(errorCode: Int, message: String) {
                XLog.w(TAG, "STT error: $message")
                runOnUiThread {
                    isRecording = false
                    updateVoiceButton(false)
                    tvLastCmd.text = "语音错误"
                }
            }
        }
    }

    private fun startRecording() {
        if (KVUtils.isSttUseLocal()) {
            startLocalRecording()
        } else {
            startHttpRecording()
        }
    }

    private fun startLocalRecording() {
        // 复用同一个 LocalSpeechRecognizer 实例，避免频繁创建销毁导致识别器忙
        if (localRecognizer == null) {
            val recognizer = LocalSpeechRecognizer(this)
            recognizer.listener = object : LocalSpeechRecognizer.Listener {
                override fun onRecordingStarted() {
                    runOnUiThread {
                        isRecording = true
                        updateVoiceButton(true)
                        tvLastCmd.text = "聆听中(本地)..."
                    }
                }

                override fun onTranscribing() {
                    runOnUiThread {
                        isRecording = false
                        updateVoiceButton(false)
                        tvLastCmd.text = "识别中..."
                    }
                }

                override fun onResult(text: String) {
                    runOnUiThread {
                        isRecording = false
                        updateVoiceButton(false)
                        processVoiceCommand(text)
                    }
                }

                override fun onError(message: String) {
                    XLog.w(TAG, "Local STT error: $message")
                    runOnUiThread {
                        isRecording = false
                        updateVoiceButton(false)
                        tvLastCmd.text = message
                    }
                }
            }
            localRecognizer = recognizer
        }
        localRecognizer?.startListening()
    }

    private fun startHttpRecording() {
        voiceController?.destroy()
        val controller = VoiceInputController(this)
        controller.listener = object : VoiceInputController.Listener {
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
                    updateVoiceButton(false)
                    tvLastCmd.text = "识别中..."
                }
            }

            override fun onFinalResult(text: String) {
                runOnUiThread { processVoiceCommand(text) }
            }

            override fun onError(errorCode: Int, message: String) {
                XLog.w(TAG, "STT error: $message")
                runOnUiThread {
                    isRecording = false
                    updateVoiceButton(false)
                    tvLastCmd.text = "语音错误"
                }
            }
        }
        voiceController = controller
        controller.startListening()
    }

    private fun stopRecording() {
        if (KVUtils.isSttUseLocal()) {
            // 本地识别：调用 stopListening 让系统完成录音并返回识别结果
            localRecognizer?.stopListening()
        } else {
            voiceController?.stopListening()
        }
        isRecording = false
        updateVoiceButton(false)
        tvLastCmd.text = "识别中..."
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

    /**
     * 汉字转拼音（小写无调）
     * 优先使用 android.icu.text.Transliterator（API 29+），
     * 回退使用内置常用字拼音表
     */
    private fun toPinyin(text: String): String {
        if (text.isEmpty()) return ""
        // 先检查是否全是非中文（英文/数字/符号），直接返回
        if (text.all { it.code < 0x4E00 || it.code > 0x9FFF }) return text.lowercase()
        // API 29+ 使用 ICU Transliterator
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

    /**
     * 回退拼音表（小车控制常用字）
     */
    private fun fallbackPinyin(text: String): String {
        val map = mapOf(
            '前' to "qian", '后' to "hou", '左' to "zuo", '右' to "you",
            '停' to "ting", '止' to "zhi", '进' to "jin", '退' to "tui",
            '转' to "zhuan", '走' to "zou", '边' to "bian", '向' to "xiang",
            '开' to "kai", '关' to "guan", '快' to "kuai", '慢' to "man",
            '上' to "shang", '下' to "xia", '起' to "qi", '倒' to "dao",
            '前' to "qian", '请' to "qing", '往' to "wang", '冲' to "chong",
            '前' to "qian", '行' to "xing",
        )
        return text.map { map[it] ?: it.toString() }.joinToString("").lowercase()
    }

    /**
     * 计算两个拼音字符串的 Levenshtein 编辑距离
     */
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

    /**
     * 拼音相似度匹配：先精确中文包含匹配，再转拼音做包含/编辑距离比较
     * @param text 语音识别结果原文
     * @param keyword 配置的关键词原文
     * @return 相似度分数 0~1
     */
    private fun pinyinScore(text: String, keyword: String): Float {
        if (text.isEmpty() || keyword.isEmpty()) return 0f
        // 1. 精确中文包含 → 满分
        if (text.contains(keyword)) return 1.0f
        // 2. 转拼音比较
        val textPy = toPinyin(text)
        val kwPy = toPinyin(keyword)
        // 拼音包含 → 满分（如"往前走" → "qianwangzou" 包含 "qian"）
        if (textPy.contains(kwPy)) return 1.0f
        // 3. 拼音编辑距离：滑动窗口取最高分
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
     * 例如"左边"(zuobian) vs "右边"(youbian) 拼音差异大，不会误判
     */
    private fun processVoiceCommand(text: String) {
        XLog.i(TAG, "Voice result: $text")
        val trimmed = text.trim()
        tvLastCmd.text = "\"$trimmed\""

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

        for ((keyword, action) in keywords) {
            val score = pinyinScore(trimmed, keyword)
            XLog.i(TAG, "  keyword='$keyword' pinyin='${toPinyin(keyword)}' score=$score")
            if (score > bestScore) {
                bestScore = score
                bestAction = action
            }
        }

        if (bestAction != null) {
            bestAction.invoke()
        } else {
            XLog.i(TAG, "No keyword matched (threshold=$threshold)")
        }
    }

    // ==================== 指令执行 ====================

    private fun speakTts(text: String) {
        ttsManager?.stop()  // 先清除去重缓存，确保重复指令也能播报
        ttsManager?.speak(text)
    }

    private fun executeForward() {
        handler.removeCallbacks(sendRunnable)
        verticalPercent = 1f
        horizontalPercent = 0f
        joystickVertical.setPercentAnimated(1f, 200L)
        joystickHorizontal.setPercentAnimated(0f, 200L)
        sendCommand("forw", 100)
        tvLastCmd.text = "前进 100%"
        tvSpeed.text = "100%"
        speakTts("已前进")
        // 2秒后自动回中
        handler.postDelayed({
            autoReturnToCenter()
        }, 2000)
    }

    private fun executeBackward() {
        handler.removeCallbacks(sendRunnable)
        verticalPercent = -1f
        horizontalPercent = 0f
        joystickVertical.setPercentAnimated(-1f, 200L)
        joystickHorizontal.setPercentAnimated(0f, 200L)
        sendCommand("back", 100)
        tvLastCmd.text = "后退 100%"
        tvSpeed.text = "100%"
        speakTts("已后退")
        handler.postDelayed({
            autoReturnToCenter()
        }, 2000)
    }

    private fun executeLeft() {
        handler.removeCallbacks(sendRunnable)
        verticalPercent = 0f
        horizontalPercent = -1f
        joystickVertical.setPercentAnimated(0f, 200L)
        joystickHorizontal.setPercentAnimated(-1f, 200L)
        sendCommand("left", 100)
        tvLastCmd.text = "左转 100%"
        tvSpeed.text = "100%"
        speakTts("已左转")
        handler.postDelayed({
            autoReturnToCenter()
        }, 2000)
    }

    private fun executeRight() {
        handler.removeCallbacks(sendRunnable)
        verticalPercent = 0f
        horizontalPercent = 1f
        joystickVertical.setPercentAnimated(0f, 200L)
        joystickHorizontal.setPercentAnimated(1f, 200L)
        sendCommand("right", 100)
        tvLastCmd.text = "右转 100%"
        tvSpeed.text = "100%"
        speakTts("已右转")
        handler.postDelayed({
            autoReturnToCenter()
        }, 2000)
    }

    private fun executeStop() {
        handler.removeCallbacks(sendRunnable)
        verticalPercent = 0f
        horizontalPercent = 0f
        joystickVertical.setPercentAnimated(0f, 200L)
        joystickHorizontal.setPercentAnimated(0f, 200L)
        sendCommand("stop")
        tvLastCmd.text = "已停止"
        tvSpeed.text = "0%"
        speakTts("已停止")
    }

    /**
     * 自动回中并发送停止指令
     */
    private fun autoReturnToCenter() {
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
