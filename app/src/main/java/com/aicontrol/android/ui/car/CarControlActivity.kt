package com.aicontrol.android.ui.car

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.aicontrol.android.R
import com.aicontrol.android.base.BaseActivity
import com.aicontrol.android.widget.CommonToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL

/**
 * 小车控制界面 - 横屏模式
 * 连接到小车 WiFi AP (192.168.4.1) 后通过 HTTP 控制方向和速度
 */
class CarControlActivity : BaseActivity() {

    companion object {
        private const val TAG = "CarControl"
        private const val CAR_HOST = "192.168.4.1"
        private const val CAR_PORT = 80
        private const val PING_INTERVAL_MS = 3000L
    }

    private lateinit var ivWifiStatus: ImageView
    private lateinit var tvWifiStatus: TextView
    private lateinit var tvLastCmd: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var speedBar: SeekBar

    private val handler = Handler(Looper.getMainLooper())
    private var isConnected = false
    private var currentSpeed = 50

    private val pingRunnable = object : Runnable {
        override fun run() {
            checkConnection()
            handler.postDelayed(this, PING_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 强制横屏
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // 全屏沉浸式
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_car_control)
        initViews()
    }

    override fun onResume() {
        super.onResume()
        handler.post(pingRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(pingRunnable)
    }

    private fun initViews() {
        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle("Car Control")
            showBackButton()
        }

        ivWifiStatus = findViewById(R.id.ivWifiStatus)
        tvWifiStatus = findViewById(R.id.tvWifiStatus)
        tvLastCmd = findViewById(R.id.tvLastCmd)
        tvSpeed = findViewById(R.id.tvSpeed)
        speedBar = findViewById(R.id.speedBar)

        // 速度滑块
        speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentSpeed = progress.coerceIn(10, 100)
                tvSpeed.text = "$currentSpeed%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 方向按钮 — 按下发送指令，松开发送停止
        setupDirectionButton(R.id.btnForward, "forw")
        setupDirectionButton(R.id.btnBack, "back")
        setupDirectionButton(R.id.btnLeft, "left")
        setupDirectionButton(R.id.btnRight, "right")

        // 停止按钮
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            sendCommand("stop")
        }
    }

    /**
     * 配置方向按钮：按下时持续发送方向指令，松开时发送停止
     */
    private fun setupDirectionButton(buttonId: Int, direction: String) {
        val btn = findViewById<Button>(buttonId)
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    sendCommand(direction)
                    tvLastCmd.text = "$direction $currentSpeed%"
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    sendCommand("stop")
                    tvLastCmd.text = "STOP"
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 发送控制指令到小车
     * HTTP GET: http://192.168.4.1/control/{direction}_{speed}
     */
    private fun sendCommand(direction: String) {
        val urlStr = "http://$CAR_HOST:$CAR_PORT/control/${direction}_${currentSpeed}"
        tvLastCmd.text = "$direction $currentSpeed%"

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = URL(urlStr)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    conn.requestMethod = "GET"
                    conn.responseCode // 触发请求
                    conn.disconnect()
                }
            } catch (e: Exception) {
                // 静默失败，避免频繁 Toast 干扰操作
            }
        }
    }

    /**
     * Ping 192.168.4.1 检测小车是否在线
     */
    private fun checkConnection() {
        lifecycleScope.launch {
            val reachable = withContext(Dispatchers.IO) {
                try {
                    val address = InetAddress.getByName(CAR_HOST)
                    address.isReachable(1500)
                } catch (e: Exception) {
                    false
                }
            }
            updateConnectionStatus(reachable)
        }
    }

    private fun updateConnectionStatus(connected: Boolean) {
        isConnected = connected
        if (connected) {
            ivWifiStatus.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4ade80"))
            tvWifiStatus.text = "Connected"
            tvWifiStatus.setTextColor(Color.parseColor("#4ade80"))
        } else {
            ivWifiStatus.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#666666"))
            tvWifiStatus.text = "Disconnected"
            tvWifiStatus.setTextColor(Color.parseColor("#888888"))
        }
    }
}
