package com.aicontrol.android.m8.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.aicontrol.android.R;
import com.aicontrol.android.base.BaseActivity;
import com.aicontrol.android.m8.utils.M8Config;
import com.aicontrol.android.widget.CommonToolbar;
import com.aicontrol.android.widget.KButton;

import java.util.regex.Pattern;

/**
 * M8/H8 Drone configuration page.
 * Allows user to set IP, video stream (UDP), control (TCP), and FTP ports.
 * Same visual style as LLM config and Drone config pages.
 *
 * Default values from decompiled H8 APK (HY-Chip Technology):
 * - IP: 192.168.100.1
 * - Video port (UDP): 1563 (Live555 RTSP/RTP H.264/H.265)
 * - Control port (TCP): 4646 (JSON commands)
 * - FTP port: 21
 */
public class M8ConfigActivity extends BaseActivity {

    private static final String TAG = "M8ConfigActivity";

    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    private EditText etM8Ip;
    private EditText etUdpPort;
    private EditText etTcpPort;
    private EditText etFtpPort;
    private TextView tvVideoUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_m8_config);

        CommonToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("M8 无人机配置");
        toolbar.showBackButton(true, () -> { finish(); return kotlin.Unit.INSTANCE; });

        etM8Ip = findViewById(R.id.etM8Ip);
        etUdpPort = findViewById(R.id.etUdpPort);
        etTcpPort = findViewById(R.id.etTcpPort);
        etFtpPort = findViewById(R.id.etFtpPort);
        tvVideoUrl = findViewById(R.id.tvVideoUrl);

        // Load saved config
        M8Config.Config config = M8Config.loadConfig(this);
        etM8Ip.setText(config.ip);
        etUdpPort.setText(String.valueOf(config.udpPort));
        etTcpPort.setText(String.valueOf(config.tcpPort));
        etFtpPort.setText(String.valueOf(config.ftpPort));

        // Update URL preview on text changes
        TextWatcher urlUpdater = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateVideoUrlPreview();
            }
        };
        etM8Ip.addTextChangedListener(urlUpdater);
        etUdpPort.addTextChangedListener(urlUpdater);

        updateVideoUrlPreview();

        // Save button
        KButton btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig();
            }
        });

        // Restore defaults button
        KButton btnRestore = findViewById(R.id.btnRestore);
        btnRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restoreDefaults();
            }
        });
    }

    private void updateVideoUrlPreview() {
        String ip = etM8Ip.getText().toString().trim();
        String port = etUdpPort.getText().toString().trim();
        if (TextUtils.isEmpty(ip)) ip = M8Config.DEFAULT_IP;
        if (TextUtils.isEmpty(port)) port = String.valueOf(M8Config.DEFAULT_UDP_PORT);
        tvVideoUrl.setText(ip + ":" + port + " (UDP视频流)");
    }

    private void saveConfig() {
        String ip = etM8Ip.getText().toString().trim();
        String udpStr = etUdpPort.getText().toString().trim();
        String tcpStr = etTcpPort.getText().toString().trim();
        String ftpStr = etFtpPort.getText().toString().trim();

        // Validate IP
        if (TextUtils.isEmpty(ip)) {
            Toast.makeText(this, "请输入设备IP地址", Toast.LENGTH_SHORT).show();
            etM8Ip.requestFocus();
            return;
        }
        if (!IP_PATTERN.matcher(ip).matches()) {
            Toast.makeText(this, "IP地址格式不正确", Toast.LENGTH_SHORT).show();
            etM8Ip.requestFocus();
            return;
        }

        // Validate UDP port
        int udpPort;
        if (TextUtils.isEmpty(udpStr)) {
            udpPort = M8Config.DEFAULT_UDP_PORT;
        } else {
            try {
                udpPort = Integer.parseInt(udpStr);
                if (udpPort < 1 || udpPort > 65535) {
                    Toast.makeText(this, "视频流端口范围: 1-65535", Toast.LENGTH_SHORT).show();
                    etUdpPort.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "视频流端口格式不正确", Toast.LENGTH_SHORT).show();
                etUdpPort.requestFocus();
                return;
            }
        }

        // Validate TCP port
        int tcpPort;
        if (TextUtils.isEmpty(tcpStr)) {
            tcpPort = M8Config.DEFAULT_TCP_PORT;
        } else {
            try {
                tcpPort = Integer.parseInt(tcpStr);
                if (tcpPort < 1 || tcpPort > 65535) {
                    Toast.makeText(this, "指令端口范围: 1-65535", Toast.LENGTH_SHORT).show();
                    etTcpPort.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "指令端口格式不正确", Toast.LENGTH_SHORT).show();
                etTcpPort.requestFocus();
                return;
            }
        }

        // Validate FTP port
        int ftpPort;
        if (TextUtils.isEmpty(ftpStr)) {
            ftpPort = M8Config.DEFAULT_FTP_PORT;
        } else {
            try {
                ftpPort = Integer.parseInt(ftpStr);
                if (ftpPort < 1 || ftpPort > 65535) {
                    Toast.makeText(this, "FTP端口范围: 1-65535", Toast.LENGTH_SHORT).show();
                    etFtpPort.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "FTP端口格式不正确", Toast.LENGTH_SHORT).show();
                etFtpPort.requestFocus();
                return;
            }
        }

        // Save
        M8Config.Config config = new M8Config.Config(ip, udpPort, tcpPort, ftpPort);
        M8Config.saveConfig(this, config);
        Toast.makeText(this, "M8 配置已保存", Toast.LENGTH_SHORT).show();
        updateVideoUrlPreview();
    }

    private void restoreDefaults() {
        etM8Ip.setText(M8Config.DEFAULT_IP);
        etUdpPort.setText(String.valueOf(M8Config.DEFAULT_UDP_PORT));
        etTcpPort.setText(String.valueOf(M8Config.DEFAULT_TCP_PORT));
        etFtpPort.setText(String.valueOf(M8Config.DEFAULT_FTP_PORT));

        M8Config.saveConfig(this, new M8Config.Config());
        Toast.makeText(this, "已恢复默认配置", Toast.LENGTH_SHORT).show();
        updateVideoUrlPreview();
    }
}
