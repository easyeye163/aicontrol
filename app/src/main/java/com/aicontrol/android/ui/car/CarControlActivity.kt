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
        // 如果正在录音，停止录音并放弃识别
        if (isRecording) {
            voiceController?.destroy()
            voiceController = null
            isRecording = false
            updateVoiceButton(false)
            tvLastCmd.text = "就绪"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sendCommand("stop")
        voiceController?.destroy()
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
                    if (!KVUtils.hasSttConfig()) {
                        Toast.makeText(this, "请先配置STT语音识别（设置 > 模型 > STT配置）", Toast.LENGTH_LONG).show()
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
        // 每次重新创建 VoiceInputController，确保干净状态
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
        voiceController?.stopListening()
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

    // ==================== 模糊匹配 ====================

    /**
     * 计算两个字符串的 Levenshtein 编辑距离
     */
    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
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
     * 模糊匹配：先精确包含匹配，再滑动窗口编辑距离匹配
     * @param text 语音识别结果
     * @param keyword 配置的关键词
     * @param threshold 相似度阈值（0~1），默认 0.5
     */
    private fun fuzzyMatch(text: String, keyword: String, threshold: Float = 0.5f): Boolean {
        if (text.isEmpty() || keyword.isEmpty()) return false
        // 1. 精确包含匹配
        if (text.contains(keyword)) return true
        // 2. 滑动窗口：在 text 中找与 keyword 长度相近的子串进行编辑距离比较
        val kwLen = keyword.length
        val textLen = text.length
        for (winLen in maxOf(1, kwLen - 1)..minOf(textLen, kwLen + 1)) {
            for (i in 0..textLen - winLen) {
                val sub = text.substring(i, i + winLen)
                val dist = levenshtein(sub, keyword)
                val maxLen = maxOf(sub.length, keyword.length)
                if (1.0f - dist.toFloat() / maxLen >= threshold) return true
            }
        }
        return false
    }

    /**
     * 处理语音识别结果，模糊匹配关键词执行控制
     */
    private fun processVoiceCommand(text: String) {
        XLog.i(TAG, "Voice result: $text")
        val trimmed = text.trim()
        tvLastCmd.text = "\"$trimmed\""

        val kwForward = KVUtils.getCarKeywordForward()
        val kwBackward = KVUtils.getCarKeywordBackward()
        val kwLeft = KVUtils.getCarKeywordLeft()
        val kwRight = KVUtils.getCarKeywordRight()
        val kwStop = KVUtils.getCarKeywordStop()

        when {
            fuzzyMatch(trimmed, kwForward) -> executeForward()
            fuzzyMatch(trimmed, kwBackward) -> executeBackward()
            fuzzyMatch(trimmed, kwLeft) -> executeLeft()
            fuzzyMatch(trimmed, kwRight) -> executeRight()
            fuzzyMatch(trimmed, kwStop) -> executeStop()
            else -> {
                XLog.i(TAG, "No keyword matched")
            }
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
