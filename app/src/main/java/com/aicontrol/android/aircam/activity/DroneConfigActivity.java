package com.aicontrol.android.aircam.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.aicontrol.android.R;
import com.aicontrol.android.aircam.base.BaseActivity;
import com.aicontrol.android.aircam.utils.DroneConfig;
import com.aicontrol.android.widget.CommonToolbar;
import com.aicontrol.android.widget.KButton;

import java.util.regex.Pattern;

public class DroneConfigActivity extends BaseActivity {

    private static final String TAG = "DroneConfigActivity";

    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

    private EditText etDroneIp;
    private EditText etUdpPort;
    private EditText etTcpPort;
    private TextView tvPatchStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drone_config);

        CommonToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("无人机连接配置");
        toolbar.showBackButton(true, () -> { finish(); return kotlin.Unit.INSTANCE; });

        etDroneIp = findViewById(R.id.etDroneIp);
        etUdpPort = findViewById(R.id.etUdpPort);
        etTcpPort = findViewById(R.id.etTcpPort);
        tvPatchStatus = findViewById(R.id.tvPatchStatus);

        // Load saved config
        DroneConfig.Config config = DroneConfig.loadConfig(this);
        etDroneIp.setText(config.ip);
        etUdpPort.setText(String.valueOf(config.udpPort));
        etTcpPort.setText(String.valueOf(config.tcpPort));

        // Show patch status
        updatePatchStatus();

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

    private void updatePatchStatus() {
        DroneConfig.PatchInfo info = DroneConfig.getPatchInfo(this);
        StringBuilder sb = new StringBuilder();
        sb.append("libCamera.so 补丁: ");
        if (info.patched) {
            sb.append("已应用\n");
            sb.append("当前IP: ").append(info.currentIp);
            sb.append("\n原始IP: ").append(info.originalIp);
        } else {
            sb.append("未应用\n");
            sb.append("使用默认IP: ").append(info.currentIp);
        }
        sb.append("\n补丁版本: ").append(info.version);
        tvPatchStatus.setText(sb.toString());
    }

    private void saveConfig() {
        String ip = etDroneIp.getText().toString().trim();
        String udpStr = etUdpPort.getText().toString().trim();
        String tcpStr = etTcpPort.getText().toString().trim();

        // Validate IP
        if (TextUtils.isEmpty(ip)) {
            Toast.makeText(this, "请输入设备IP地址", Toast.LENGTH_SHORT).show();
            etDroneIp.requestFocus();
            return;
        }
        if (!IP_PATTERN.matcher(ip).matches()) {
            Toast.makeText(this, "IP地址格式不正确", Toast.LENGTH_SHORT).show();
            etDroneIp.requestFocus();
            return;
        }
        // IP must be <= 15 chars to fit in binary patch area
        if (ip.length() > 15) {
            Toast.makeText(this, "IP地址过长（最多15字符）", Toast.LENGTH_SHORT).show();
            etDroneIp.requestFocus();
            return;
        }

        // Validate UDP port
        int udpPort;
        if (TextUtils.isEmpty(udpStr)) {
            udpPort = DroneConfig.DEFAULT_UDP_PORT;
        } else {
            try {
                udpPort = Integer.parseInt(udpStr);
                if (udpPort < 1 || udpPort > 65535) {
                    Toast.makeText(this, "UDP端口范围: 1-65535", Toast.LENGTH_SHORT).show();
                    etUdpPort.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "UDP端口格式不正确", Toast.LENGTH_SHORT).show();
                etUdpPort.requestFocus();
                return;
            }
        }

        // Validate TCP port
        int tcpPort;
        if (TextUtils.isEmpty(tcpStr)) {
            tcpPort = DroneConfig.DEFAULT_TCP_PORT;
        } else {
            try {
                tcpPort = Integer.parseInt(tcpStr);
                if (tcpPort < 1 || tcpPort > 65535) {
                    Toast.makeText(this, "TCP端口范围: 1-65535", Toast.LENGTH_SHORT).show();
                    etTcpPort.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "TCP端口格式不正确", Toast.LENGTH_SHORT).show();
                etTcpPort.requestFocus();
                return;
            }
        }

        // Save to SharedPreferences
        DroneConfig.Config config = new DroneConfig.Config(ip, udpPort, tcpPort);
        DroneConfig.saveConfig(this, config);

        // Apply binary patch to libCamera.so
        boolean patched = DroneConfig.applyPatch(this, ip);
        if (patched) {
            Toast.makeText(this, "配置已保存，补丁已应用。重启应用后生效。", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "配置已保存，补丁应用失败（将使用配置IP）。重启应用后生效。", Toast.LENGTH_LONG).show();
        }

        updatePatchStatus();
    }

    private void restoreDefaults() {
        etDroneIp.setText(DroneConfig.DEFAULT_IP);
        etUdpPort.setText(String.valueOf(DroneConfig.DEFAULT_UDP_PORT));
        etTcpPort.setText(String.valueOf(DroneConfig.DEFAULT_TCP_PORT));

        DroneConfig.saveConfig(this, new DroneConfig.Config());
        boolean restored = DroneConfig.applyPatch(this, DroneConfig.DEFAULT_IP);
        if (restored) {
            Toast.makeText(this, "已恢复默认配置，重启应用后生效", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "已恢复默认配置（补丁恢复失败）", Toast.LENGTH_LONG).show();
        }
        updatePatchStatus();
    }
}
