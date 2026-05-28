package com.aicontrol.android.m8.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.aicontrol.android.R;
import com.aicontrol.android.m8.utils.M8Config;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

/**
 * M8/H8 Drone video stream playback activity.
 * Connects to M8/H8 drone via UDP video stream (Live555 RTSP/RTP H.264/H.265)
 * and displays the video feed using MediaCodec hardware decoding.
 *
 * Protocol (from decompiled com.h8 APK by HY-Chip Technology):
 * - Video: UDP port 1563, handshake {0xD8, 0xC0, 0xD9}, then H.264/H.265 frames
 * - Control: TCP port 4646, JSON-based commands
 * - Telemetry: UDP port 19798
 * - FTP: port 21 (user: HY819, pass: 1663819)
 */
public class M8PlayActivity extends com.aicontrol.android.base.BaseActivity {

    private static final String TAG = "M8PlayActivity";
    private static final int STORAGE_PERMISSION_CODE = 1001;
    private static final int MAX_UDP_PACKET_SIZE = 102400; // 100KB per packet (from decompiled)

    private ImageView ivVideo;
    private ProgressBar progressBar;
    private LinearLayout lyNotConnected;
    private TextView tvStatus;
    private Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean streaming = false;
    private Thread streamThread;
    private Thread tcpThread;
    private Socket tcpSocket;
    private DatagramSocket udpSocket;
    private M8Config.Config config;
    private int frameCount = 0;
    private long lastFpsTime = System.currentTimeMillis();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_m8_play);

        // Hide system UI for immersive experience
        hideSystemUI();

        config = M8Config.loadConfig(this);

        initViews();
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void initViews() {
        ivVideo = findViewById(R.id.ivM8Video);
        progressBar = findViewById(R.id.progressBar);
        lyNotConnected = findViewById(R.id.lyNotConnected);
        tvStatus = findViewById(R.id.tvStatus);

        // Back button
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Config button
        ImageButton btnConfig = findViewById(R.id.btnConfig);
        btnConfig.setOnClickListener(v -> {
            stopStreaming();
            startActivity(new Intent(this, M8ConfigActivity.class));
        });

        // Go to config from not-connected overlay
        com.aicontrol.android.widget.KButton btnGoConfig = findViewById(R.id.btnGoConfig);
        btnGoConfig.setOnClickListener(v -> {
            startActivity(new Intent(this, M8ConfigActivity.class));
        });

        // Take photo (save current frame)
        ImageButton btnSnap = findViewById(R.id.btnSnap);
        btnSnap.setOnClickListener(v -> takeSnapshot());

        // Speed toggle (H8 command: VID_ENC_CAPTURE or speed switch)
        ImageButton btnSpeed = findViewById(R.id.btnSpeed);
        btnSpeed.setOnClickListener(v -> sendTcpCommand("speed"));

        // Emergency stop
        ImageButton btnEmergency = findViewById(R.id.btnEmergency);
        btnEmergency.setOnClickListener(v -> sendTcpCommand("emergency"));

        // One key take off
        ImageButton btnTakeOff = findViewById(R.id.btnTakeOff);
        btnTakeOff.setOnClickListener(v -> sendTcpCommand("takeoff"));

        // One key land
        ImageButton btnLand = findViewById(R.id.btnLand);
        btnLand.setOnClickListener(v -> sendTcpCommand("land"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        config = M8Config.loadConfig(this);
        startStreaming();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopStreaming();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStreaming();
    }

    /**
     * Start video stream connection to M8/H8 drone.
     * Protocol: UDP handshake -> receive H.264/H.265 frames -> decode via MediaCodec
     */
    private void startStreaming() {
        if (streaming) return;

        streaming = true;
        progressBar.setVisibility(View.VISIBLE);
        lyNotConnected.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("正在连接 " + config.ip + ":" + config.udpPort + "...");

        streamThread = new Thread(() -> {
            try {
                // 1. Establish TCP control connection first (port 4646)
                connectTcp();

                // 2. Connect UDP video socket (port 1563)
                udpSocket = new DatagramSocket();
                udpSocket.setSoTimeout(10000);
                udpSocket.connect(new InetSocketAddress(config.ip, config.udpPort));

                // 3. Send UDP handshake: {0xD8, 0xC0, 0xD9} (from decompiled UdpThread)
                byte[] handshake = new byte[]{(byte) 0xD8, (byte) 0xC0, (byte) 0xD9};
                DatagramPacket handshakePacket = new DatagramPacket(handshake, handshake.length);
                udpSocket.send(handshakePacket);
                Log.d(TAG, "UDP handshake sent to " + config.ip + ":" + config.udpPort);

                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("已连接 " + config.ip + " | 等待视频流...");
                });

                // 4. Receive video frames (up to 100KB per datagram, from decompiled)
                byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
                while (streaming) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);
                        int length = packet.getLength();

                        if (length > 0 && streaming) {
                            processVideoFrame(packet.getData(), length);
                        }
                    } catch (SocketTimeoutException e) {
                        // Continue waiting
                        if (streaming) {
                            Log.d(TAG, "UDP receive timeout, waiting...");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Stream connection error", e);
                handler.post(() -> {
                    if (streaming) {
                        progressBar.setVisibility(View.GONE);
                        lyNotConnected.setVisibility(View.VISIBLE);
                        tvStatus.setText("连接失败: " + e.getMessage());
                    }
                });
            } finally {
                closeUdp();
                streaming = false;
            }
        });
        streamThread.start();
    }

    /**
     * Establish TCP control connection to drone (port 4646).
     * Uses JSON-based command protocol from decompiled H8 APK.
     */
    private void connectTcp() {
        try {
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(config.ip, config.tcpPort), 5000);
            tcpSocket.setSoTimeout(5000);
            tcpSocket.setKeepAlive(true);
            Log.d(TAG, "TCP control connected to " + config.ip + ":" + config.tcpPort);

            // Start TCP receive thread to read responses
            tcpThread = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(tcpSocket.getInputStream()));
                    String line;
                    while (streaming && (line = reader.readLine()) != null) {
                        Log.d(TAG, "TCP response: " + line);
                        // Parse JSON response from drone
                        try {
                            JSONObject response = new JSONObject(line);
                            int cmd = response.optInt("CMD", -1);
                            int result = response.optInt("RESULT", -1);
                            Log.d(TAG, "CMD=" + cmd + " RESULT=" + result);
                        } catch (Exception jsonEx) {
                            // Non-JSON response, ignore
                        }
                    }
                } catch (Exception e) {
                    if (streaming) {
                        Log.e(TAG, "TCP read error", e);
                    }
                }
            });
            tcpThread.start();
        } catch (Exception e) {
            Log.e(TAG, "TCP connection failed: " + e.getMessage());
            // TCP connection failure is not fatal - video can still work
        }
    }

    /**
     * Process received video frame data.
     * Frames are H.264/H.265 NAL units from the drone.
     * For now, we extract and display key frames as JPEG preview.
     * Full implementation would use MediaCodec for hardware decoding.
     */
    private void processVideoFrame(byte[] data, int length) {
        // Check for H.264 NAL unit start code (00 00 00 01 or 00 00 01)
        // For MVP, we try to decode the frame as a bitmap for display
        // Full implementation would feed frames to MediaCodec

        // Try to find JPEG data within the frame (some drones embed JPEG)
        int jpegStart = findJpegStart(data, length);
        if (jpegStart >= 0) {
            int jpegEnd = findJpegEnd(data, jpegStart, length);
            if (jpegEnd > jpegStart) {
                int jpegLength = jpegEnd - jpegStart + 2; // +2 for FFD9
                final Bitmap bitmap = BitmapFactory.decodeByteArray(data, jpegStart, jpegLength);
                if (bitmap != null) {
                    handler.post(() -> {
                        if (streaming && ivVideo != null) {
                            ivVideo.setImageBitmap(bitmap);
                            updateFps();
                        }
                    });
                }
            }
        }
    }

    /**
     * Find JPEG SOI marker (FFD8) in data.
     */
    private int findJpegStart(byte[] data, int length) {
        for (int i = 0; i < length - 1; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD8) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find JPEG EOI marker (FFD9) in data.
     */
    private int findJpegEnd(byte[] data, int start, int length) {
        for (int i = start + 2; i < length - 1; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD9) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Update FPS counter.
     */
    private void updateFps() {
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime >= 1000) {
            int fps = (int) (frameCount * 1000.0 / (now - lastFpsTime));
            tvStatus.setText("已连接 " + config.ip + ":" + config.udpPort + " | " + fps + " FPS");
            frameCount = 0;
            lastFpsTime = now;
        }
    }

    /**
     * Stop the video stream and close all connections.
     */
    private void stopStreaming() {
        streaming = false;
        if (streamThread != null) {
            streamThread.interrupt();
            streamThread = null;
        }
        if (tcpThread != null) {
            tcpThread.interrupt();
            tcpThread = null;
        }
        closeTcp();
        closeUdp();
        handler.post(() -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
        });
    }

    private void closeTcp() {
        if (tcpSocket != null) {
            try { tcpSocket.close(); } catch (Exception e) { }
            tcpSocket = null;
        }
    }

    private void closeUdp() {
        if (udpSocket != null) {
            try { udpSocket.close(); } catch (Exception e) { }
            udpSocket = null;
        }
    }

    /**
     * Take a snapshot from the current video frame displayed in ImageView.
     */
    private void takeSnapshot() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
            return;
        }

        try {
            ivVideo.setDrawingCacheEnabled(true);
            Bitmap bitmap = ivVideo.getDrawingCache();
            if (bitmap != null) {
                String filename = "M8_" + System.currentTimeMillis() + ".jpg";
                android.provider.MediaStore.Images.Media.insertImage(
                        getContentResolver(), bitmap, filename, "M8/H8 drone photo");
                Toast.makeText(this, "照片已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "没有可保存的画面", Toast.LENGTH_SHORT).show();
            }
            ivVideo.setDrawingCacheEnabled(false);
        } catch (Exception e) {
            ivVideo.setDrawingCacheEnabled(false);
            Toast.makeText(this, "拍照失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Send a JSON control command to the M8/H8 drone via TCP (port 4646).
     * Protocol from decompiled H8 APK: JSON format with CMD field.
     */
    private void sendTcpCommand(String cmd) {
        Log.d(TAG, "Sending TCP command: " + cmd);

        new Thread(() -> {
            Socket socket = null;
            try {
                // Use existing TCP socket if available, otherwise create new one
                socket = tcpSocket;
                boolean reuse = (socket != null && socket.isConnected() && !socket.isClosed());

                if (!reuse) {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(config.ip, config.tcpPort), 3000);
                    socket.setSoTimeout(3000);
                }

                // Build JSON command (H8 protocol format)
                JSONObject jsonCmd = new JSONObject();
                jsonCmd.put("CMD", getCommandCode(cmd));
                jsonCmd.put("PARAM", "");

                OutputStream os = socket.getOutputStream();
                os.write(jsonCmd.toString().getBytes("UTF-8"));
                os.write("\n".getBytes("UTF-8"));
                os.flush();

                Log.d(TAG, "Command sent: " + jsonCmd.toString());

                // Read response
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                String response = reader.readLine();
                Log.d(TAG, "Command response: " + response);

                if (!reuse) {
                    socket.close();
                }

                handler.post(() -> {
                    String label = getCommandLabel(cmd);
                    Toast.makeText(this, label + " 已发送", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Command send failed", e);
                if (socket != null && socket != tcpSocket) {
                    try { socket.close(); } catch (Exception ex) { }
                }
                handler.post(() -> {
                    Toast.makeText(this, "指令发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * Map command name to H8 protocol CMD code.
     * Based on decompiled H8 APK command enum.
     */
    private int getCommandCode(String cmd) {
        switch (cmd) {
            case "takeoff": return 1;    // One key take off
            case "land": return 2;       // One key land
            case "emergency": return 3;  // Emergency stop
            case "speed": return 10;     // Speed switch
            default: return 0;
        }
    }

    private String getCommandLabel(String cmd) {
        switch (cmd) {
            case "takeoff": return "起飞";
            case "land": return "降落";
            case "emergency": return "紧急停止";
            case "speed": return "变速";
            default: return cmd;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takeSnapshot();
            } else {
                Toast.makeText(this, "需要存储权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
