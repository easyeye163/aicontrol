package com.aicontrol.android.ui.tcpdump

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.aicontrol.android.R
import com.aicontrol.android.base.BaseActivity
import com.aicontrol.android.widget.CommonToolbar
import com.aicontrol.android.widget.KButton

/**
 * 抓包工具页面 - 基于 VpnService（无需 Root）
 * 拦截并记录手机所有网络流量，支持 IP 过滤和 PCAP 导出
 */
class TcpDumpActivity : BaseActivity() {

    companion object {
        private const val TAG = "TcpDumpActivity"
        private const val MAX_LOG_LINES = 3000
        private const val VPN_REQUEST_CODE = 100
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvPacketCount: TextView
    private lateinit var tvCaptureTime: TextView
    private lateinit var etFilterIp: EditText
    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnStart: KButton
    private lateinit var btnStop: KButton

    private var isCapturing = false
    private var captureStartTime: Long = 0
    private var pcapFilePath: String = ""
    private var timerRunnable: java.lang.Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val logBuffer = StringBuilder()

    private val packetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PacketCaptureService.ACTION_PACKET -> handlePacket(intent)
                PacketCaptureService.ACTION_STATUS -> handleStatus(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tcpdump)
        initViews()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startCaptureService()
            } else {
                Toast.makeText(this, R.string.tcpdump_vpn_permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(PacketCaptureService.ACTION_PACKET)
            addAction(PacketCaptureService.ACTION_STATUS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packetReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packetReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(packetReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }

    private fun initViews() {
        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitleCentered(false)
            setTitle(getString(R.string.tcpdump_title))
            setBackIcon(R.drawable.ic_back) { finish() }
        }

        tvStatus = findViewById(R.id.tvTcpdumpStatus)
        tvPacketCount = findViewById(R.id.tvPacketCount)
        tvCaptureTime = findViewById(R.id.tvCaptureTime)
        etFilterIp = findViewById(R.id.etFilterIp)
        tvOutput = findViewById(R.id.tvOutput)
        scrollView = findViewById(R.id.scrollOutput)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        btnStart.setOnClickListener { requestVpnAndStart() }
        btnStop.setOnClickListener { stopCapture() }
    }

    private fun requestVpnAndStart() {
        if (isCapturing) {
            Toast.makeText(this, R.string.tcpdump_already_running, Toast.LENGTH_SHORT).show()
            return
        }

        // 请求 VPN 权限
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        } else {
            // 已经有权限
            startCaptureService()
        }
    }

    private fun startCaptureService() {
        val filterIp = etFilterIp.text.toString().trim()
        val serviceIntent = Intent(this, PacketCaptureService::class.java).apply {
            action = PacketCaptureService.ACTION_START
            putExtra(PacketCaptureService.EXTRA_FILTER_IP, filterIp)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        isCapturing = true
        captureStartTime = System.currentTimeMillis()
        logBuffer.clear()

        updateCapturingUI(true)
        appendLog("[*] VPN 抓包服务已启动")
        if (filterIp.isNotEmpty()) {
            appendLog("[*] IP 过滤: $filterIp")
        }
        appendLog("[*] PCAP 文件自动保存到 /sdcard/Android/data/com.aicontrol.android/files/pcap/")
        appendLog("[*] 抓包格式: 协议 源IP:端口 → 目标IP:端口 (长度)")
        appendLog("")
        startTimer()

        Toast.makeText(this, R.string.tcpdump_capture_started_toast, Toast.LENGTH_SHORT).show()
    }

    private fun stopCapture() {
        if (!isCapturing) return
        isCapturing = false

        val serviceIntent = Intent(this, PacketCaptureService::class.java).apply {
            action = PacketCaptureService.ACTION_STOP
        }
        startService(serviceIntent)

        timerRunnable?.let { handler.removeCallbacks(it) }
        updateCapturingUI(false)

        val elapsed = System.currentTimeMillis() - captureStartTime
        val seconds = elapsed / 1000
        appendLog("")
        appendLog("[*] 抓包已停止，时长: ${seconds}秒")
        if (pcapFilePath.isNotEmpty()) {
            appendLog("[*] PCAP: $pcapFilePath")
        }
    }

    private fun handlePacket(intent: Intent) {
        val srcIp = intent.getStringExtra(PacketCaptureService.EXTRA_SRC_IP) ?: return
        val dstIp = intent.getStringExtra(PacketCaptureService.EXTRA_DST_IP) ?: return
        val srcPort = intent.getIntExtra(PacketCaptureService.EXTRA_SRC_PORT, 0)
        val dstPort = intent.getIntExtra(PacketCaptureService.EXTRA_DST_PORT, 0)
        val protocol = intent.getStringExtra(PacketCaptureService.EXTRA_PROTOCOL) ?: "?"
        val length = intent.getIntExtra(PacketCaptureService.EXTRA_LENGTH, 0)
        val count = intent.getLongExtra(PacketCaptureService.EXTRA_COUNT, 0)

        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        val line = "[$time] $protocol $srcIp:$srcPort → $dstIp:$dstPort ($length B)"
        appendLog(line)

        tvPacketCount.text = getString(R.string.tcpdump_packet_count, count)
    }

    private fun handleStatus(intent: Intent) {
        val status = intent.getStringExtra(PacketCaptureService.EXTRA_STATUS) ?: return
        val info = intent.getStringExtra(PacketCaptureService.EXTRA_INFO) ?: ""

        when (status) {
            PacketCaptureService.STATUS_STARTED -> {
                appendLog("[*] $info")
            }
            PacketCaptureService.STATUS_STOPPED -> {
                pcapFilePath = info.substringAfter("PCAP: ").trim()
                appendLog("[*] $info")
            }
            PacketCaptureService.STATUS_ERROR -> {
                appendLog("[ERROR] $info")
                isCapturing = false
                updateCapturingUI(false)
                timerRunnable?.let { handler.removeCallbacks(it) }
            }
        }
    }

    private fun startTimer() {
        timerRunnable = object : java.lang.Runnable {
            override fun run() {
                if (!isCapturing) return
                val elapsed = System.currentTimeMillis() - captureStartTime
                val seconds = elapsed / 1000
                val minutes = seconds / 60
                val secs = seconds % 60
                tvCaptureTime.text = getString(R.string.tcpdump_capture_time, minutes, secs)
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(timerRunnable!!, 1000)
    }

    private fun appendLog(text: String) {
        logBuffer.append(text).append("\n")
        val lines = logBuffer.lines()
        if (lines.size > MAX_LOG_LINES) {
            val trimmed = lines.takeLast(MAX_LOG_LINES)
            logBuffer.clear()
            logBuffer.append(trimmed.joinToString("\n")).append("\n")
        }
        tvOutput.text = logBuffer
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun updateCapturingUI(capturing: Boolean) {
        if (capturing) {
            tvStatus.text = getString(R.string.tcpdump_status_capturing)
            tvStatus.setTextColor(getColor(R.color.colorSuccessPrimary))
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            tvPacketCount.visibility = View.VISIBLE
            tvCaptureTime.visibility = View.VISIBLE
            etFilterIp.isEnabled = false
        } else {
            tvStatus.text = getString(R.string.tcpdump_status_idle)
            tvStatus.setTextColor(getColor(R.color.colorTextPrimary))
            btnStart.isEnabled = true
            btnStop.isEnabled = false
            etFilterIp.isEnabled = true
        }
    }
}
