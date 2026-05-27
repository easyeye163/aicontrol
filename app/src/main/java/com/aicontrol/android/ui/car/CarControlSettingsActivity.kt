package com.aicontrol.android.ui.car

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import com.aicontrol.android.R
import com.aicontrol.android.base.BaseActivity
import com.aicontrol.android.utils.KVUtils
import com.aicontrol.android.widget.CommonToolbar
import com.aicontrol.android.widget.KButton

/**
 * 小车控制语音关键词设置
 */
class CarControlSettingsActivity : BaseActivity() {

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

            val port = portStr.toIntOrNull() ?: 80

            KVUtils.setCarHost(host)
            KVUtils.setCarPort(port)
            KVUtils.setCarKeywordForward(forward)
            KVUtils.setCarKeywordBackward(backward)
            KVUtils.setCarKeywordLeft(left)
            KVUtils.setCarKeywordRight(right)
            KVUtils.setCarKeywordStop(stop)

            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
