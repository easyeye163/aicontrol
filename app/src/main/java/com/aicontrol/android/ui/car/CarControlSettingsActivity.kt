package com.aicontrol.android.ui.car

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.aicontrol.android.R
import com.aicontrol.android.base.BaseActivity
import com.aicontrol.android.utils.KVUtils
import com.aicontrol.android.widget.CommonToolbar
import com.aicontrol.android.widget.KButton

/**
 * 小车控制语音关键词设置 + 视频流协议选择
 */
class CarControlSettingsActivity : BaseActivity() {

    private lateinit var spinnerProtocol: Spinner
    private lateinit var tvProtocolDescription: TextView
    private lateinit var tvProtocolStatus: TextView

    private var selectedProtocol: StreamProtocol = StreamProtocol.H264_RAW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car_control_settings)

        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle("语音控制设置")
            showBackButton(true) { finish() }
        }

        val etCarHost = findViewById<EditText>(R.id.etCarHost)
        val etCarPort = findViewById<EditText>(R.id.etCarPort)
        val etForward = findViewById<EditText>(R.id.etKeywordForward)
        val etBackward = findViewById<EditText>(R.id.etKeywordBackward)
        val etLeft = findViewById<EditText>(R.id.etKeywordLeft)
        val etRight = findViewById<EditText>(R.id.etKeywordRight)
        val etStop = findViewById<EditText>(R.id.etKeywordStop)

        // 加载已保存的配置
        etCarHost.setText(KVUtils.getCarHost())
        etCarPort.setText(KVUtils.getCarPort().toString())
        etForward.setText(KVUtils.getCarKeywordForward())
        etBackward.setText(KVUtils.getCarKeywordBackward())
        etLeft.setText(KVUtils.getCarKeywordLeft())
        etRight.setText(KVUtils.getCarKeywordRight())
        etStop.setText(KVUtils.getCarKeywordStop())

        // ====== 视频流协议 Spinner ======
        spinnerProtocol = findViewById(R.id.spinnerStreamProtocol)
        tvProtocolDescription = findViewById(R.id.tvProtocolDescription)
        tvProtocolStatus = findViewById(R.id.tvProtocolStatus)

        // 加载当前已保存的协议
        selectedProtocol = StreamProtocol.fromKey(KVUtils.getStreamProtocol())

        // 设置 Spinner 数据源
        val labels = StreamProtocol.labels()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerProtocol.adapter = adapter

        // 设置当前选中项
        val currentIndex = StreamProtocol.values().indexOf(selectedProtocol)
        if (currentIndex >= 0) {
            spinnerProtocol.setSelection(currentIndex)
        }
        updateProtocolInfo(selectedProtocol)

        // Spinner 选择监听
        spinnerProtocol.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val protocol = StreamProtocol.values()[position]
                selectedProtocol = protocol
                updateProtocolInfo(protocol)

                // 如果选择的协议尚未实现，显示提示
                if (protocol.status == ProtocolStatus.PENDING) {
                    Toast.makeText(
                        this@CarControlSettingsActivity,
                        "${protocol.label} 尚未实现，选择后视频预览可能不可用",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // ====== 保存按钮 ======
        findViewById<KButton>(R.id.btnSave).setOnClickListener {
            val host = etCarHost.text.toString().trim()
            val portStr = etCarPort.text.toString().trim()
            val forward = etForward.text.toString().trim()
            val backward = etBackward.text.toString().trim()
            val left = etLeft.text.toString().trim()
            val right = etRight.text.toString().trim()
            val stop = etStop.text.toString().trim()

            if (host.isEmpty()) {
                Toast.makeText(this, "IP 地址不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (forward.isEmpty() || backward.isEmpty() || left.isEmpty() || right.isEmpty() || stop.isEmpty()) {
                Toast.makeText(this, "所有关键词不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val port = portStr.toIntOrNull() ?: selectedProtocol.defaultPort

            // 保存小车地址配置
            KVUtils.setCarHost(host)
            KVUtils.setCarPort(port)
            KVUtils.setCarKeywordForward(forward)
            KVUtils.setCarKeywordBackward(backward)
            KVUtils.setCarKeywordLeft(left)
            KVUtils.setCarKeywordRight(right)
            KVUtils.setCarKeywordStop(stop)

            // 保存视频流协议
            KVUtils.setStreamProtocol(selectedProtocol.key)

            val statusHint = if (selectedProtocol.status == ProtocolStatus.PENDING) {
                "\n注意：${selectedProtocol.label} 尚未实现，视频预览暂不可用"
            } else ""
            Toast.makeText(this, "保存成功$statusHint", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * 更新协议描述和状态显示
     */
    private fun updateProtocolInfo(protocol: StreamProtocol) {
        tvProtocolDescription.text = protocol.description
        tvProtocolStatus.text = when (protocol.status) {
            ProtocolStatus.IMPLEMENTED -> "✓ 已实现 · 默认端口 ${protocol.defaultPort}"
            ProtocolStatus.PENDING -> "⚠ 待实现 · 默认端口 ${protocol.defaultPort}"
        }
    }
}
