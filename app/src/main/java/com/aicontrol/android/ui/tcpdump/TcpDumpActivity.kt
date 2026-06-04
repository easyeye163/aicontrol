package com.aicontrol.android.ui.tcpdump

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
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * TcpDump 抓包工具页面
 * 需要 Root 权限，使用 su 执行 tcpdump 命令
 */
class TcpDumpActivity : BaseActivity() {

    companion object {
        private const val TAG = "TcpDumpActivity"
        private const val TCPDUMP_DIR = "/data/local/tmp/aicontrol_tcpdump"
        private const val MAX_LOG_LINES = 2000
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvPacketCount: TextView
    private lateinit var tvCaptureTime: TextView
    private lateinit var etInterface: EditText
    private lateinit var etFilter: EditText
    private lateinit var tvOutput: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnStart: KButton
    private lateinit var btnStop: KButton

    private var tcpdumpProcess: Process? = null
    private var isCapturing = AtomicBoolean(false)
    private var packetCount = AtomicLong(0)
    private var captureStartTime: Long = 0
    private var outputFileName: String = ""
    private var logThread: Thread? = null
    private var timerRunnable: java.lang.Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val logBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tcpdump)

        initViews()
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
        etInterface = findViewById(R.id.etInterface)
        etFilter = findViewById(R.id.etFilter)
        tvOutput = findViewById(R.id.tvOutput)
        scrollView = findViewById(R.id.scrollOutput)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        btnStart.setOnClickListener { startCapture() }
        btnStop.setOnClickListener { stopCapture() }
    }

    private fun startCapture() {
        if (isCapturing.get()) {
            Toast.makeText(this, R.string.tcpdump_already_running, Toast.LENGTH_SHORT).show()
            return
        }

        val interfaceName = etInterface.text.toString().trim().ifEmpty { "any" }
        val filterExpr = etFilter.text.toString().trim()

        // 确保 tcpdump 可用
        val tcpdumpPath = ensureTcpdumpAvailable()
        if (tcpdumpPath == null) {
            Toast.makeText(this, R.string.tcpdump_not_found, Toast.LENGTH_LONG).show()
            return
        }

        // 检查 root 权限
        if (!checkRootAccess()) {
            Toast.makeText(this, R.string.tcpdump_no_root, Toast.LENGTH_LONG).show()
            return
        }

        // 创建输出文件目录
        val dir = File(TCPDUMP_DIR)
        try {
            val mkdirProcess = ProcessBuilder("su", "-c", "mkdir -p $TCPDUMP_DIR").start()
            mkdirProcess.waitFor()
        } catch (e: Exception) {
            // ignore
        }

        // 创建输出文件名
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        outputFileName = "capture_${timestamp}.pcap"
        val outputFile = File(TCPDUMP_DIR, outputFileName)

        // 构建命令
        val cmd = buildTcpdumpCommand(tcpdumpPath, interfaceName, filterExpr, outputFile.absolutePath)

        try {
            // 通过 su 执行
            val processBuilder = ProcessBuilder("su", "-c", cmd)
            processBuilder.redirectErrorStream(true)
            tcpdumpProcess = processBuilder.start()

            isCapturing.set(true)
            packetCount.set(0)
            captureStartTime = System.currentTimeMillis()
            logBuffer.clear()

            // 更新 UI 状态
            updateCapturingUI(true)
            tvOutput.text = getString(R.string.tcpdump_capture_started)
            appendLog("[*] tcpdump started: $cmd")
            appendLog("[*] Output: ${outputFile.absolutePath}")
            appendLog("")

            // 读取输出线程
            startLogReader(tcpdumpProcess!!)

            // 启动计时器
            startTimer()

            Toast.makeText(this, R.string.tcpdump_capture_started_toast, Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            appendLog("[ERROR] Failed to start tcpdump: ${e.message}")
            Toast.makeText(this, getString(R.string.tcpdump_start_failed, e.message), Toast.LENGTH_LONG).show()
            isCapturing.set(false)
        }
    }

    private fun stopCapture() {
        if (!isCapturing.get()) return

        try {
            tcpdumpProcess?.destroy()
            Thread {
                try {
                    tcpdumpProcess?.waitFor()
                    tcpdumpProcess?.destroyForcibly()
                } catch (e: Exception) { }
            }.start()
        } catch (e: Exception) { }

        isCapturing.set(false)
        tcpdumpProcess = null
        logThread?.interrupt()
        logThread = null

        // 停止计时器
        timerRunnable?.let { handler.removeCallbacks(it) }

        // 更新 UI
        updateCapturingUI(false)

        val elapsed = System.currentTimeMillis() - captureStartTime
        val seconds = elapsed / 1000
        appendLog("")
        appendLog("[*] Capture stopped. Duration: ${seconds}s, Packets: ${packetCount.get()}")
        appendLog("[*] PCAP saved: $TCPDUMP_DIR/$outputFileName")

        Toast.makeText(this, getString(R.string.tcpdump_capture_stopped, outputFileName), Toast.LENGTH_LONG).show()
    }

    private fun buildTcpdumpCommand(tcpdumpPath: String, iface: String, filter: String, outputFile: String): String {
        val sb = StringBuilder()
        sb.append(tcpdumpPath)
        sb.append(" -i $iface")
        sb.append(" -w $outputFile")
        sb.append(" -c 10000")
        if (filter.isNotEmpty()) {
            sb.append(" \"$filter\"")
        }
        return sb.toString()
    }

    private fun ensureTcpdumpAvailable(): String? {
        return try {
            // 检查系统是否有 tcpdump
            val checkProcess = ProcessBuilder("su", "-c", "which tcpdump").start()
            val reader = BufferedReader(InputStreamReader(checkProcess.inputStream))
            val path = reader.readLine()
            reader.close()
            checkProcess.waitFor()
            if (path != null && File(path).exists()) {
                return path
            }

            // 尝试常见路径
            val commonPaths = listOf(
                "/system/bin/tcpdump",
                "/system/xbin/tcpdump",
                "/vendor/bin/tcpdump",
                "$TCPDUMP_DIR/tcpdump"
            )
            for (p in commonPaths) {
                if (File(p).exists()) return p
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            reader.close()
            process.waitFor()
            output != null && (output.contains("uid=0") || output.contains("root"))
        } catch (e: Exception) {
            false
        }
    }

    private fun startLogReader(process: Process) {
        logThread = Thread({
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (isCapturing.get() && reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    packetCount.incrementAndGet()
                    handler.post {
                        appendLog(l)
                        tvPacketCount.text = getString(R.string.tcpdump_packet_count, packetCount.get())
                    }
                }
                reader.close()
            } catch (e: Exception) { }
        }, "TcpDumpLogReader")
        logThread?.start()
    }

    private fun startTimer() {
        timerRunnable = object : java.lang.Runnable {
            override fun run() {
                if (!isCapturing.get()) return
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
            etInterface.isEnabled = false
            etFilter.isEnabled = false
        } else {
            tvStatus.text = getString(R.string.tcpdump_status_idle)
            tvStatus.setTextColor(getColor(R.color.colorTextPrimary))
            btnStart.isEnabled = true
            btnStop.isEnabled = false
            etInterface.isEnabled = true
            etFilter.isEnabled = true
        }
    }
}
