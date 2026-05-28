package com.aicontrol.android.m8.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

import android.provider.MediaStore;

import com.aicontrol.android.R;
import com.aicontrol.android.m8.utils.M8Config;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * M8 Drone video stream playback activity.
 * Connects to M8 drone via HTTP MJPEG stream and displays the video feed.
 *
 * M8 Protocol:
 * - Video: HTTP MJPEG stream at http://<IP>:<UDP_PORT>/live
 * - Snapshot: http://<IP>:<UDP_PORT>/capture
 * - Commands: HTTP GET at http://<IP>:<TCP_PORT>/?cmd=<command>
 * - FTP: ftp://<IP>:<FTP_PORT>/ for file transfer
 */
public class M8PlayActivity extends com.aicontrol.android.base.BaseActivity {

    private static final String TAG = "M8PlayActivity";
    private static final int STORAGE_PERMISSION_CODE = 1001;

    private ImageView ivVideo;
    private ProgressBar progressBar;
    private LinearLayout lyNotConnected;
    private TextView tvStatus;
    private Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean streaming = false;
    private Thread streamThread;
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

        // Take photo
        ImageButton btnSnap = findViewById(R.id.btnSnap);
        btnSnap.setOnClickListener(v -> takeSnapshot());

        // Speed toggle
        ImageButton btnSpeed = findViewById(R.id.btnSpeed);
        btnSpeed.setOnClickListener(v -> sendCommand("speed"));

        // Emergency stop
        ImageButton btnEmergency = findViewById(R.id.btnEmergency);
        btnEmergency.setOnClickListener(v -> sendCommand("emergency"));

        // Take off
        ImageButton btnTakeOff = findViewById(R.id.btnTakeOff);
        btnTakeOff.setOnClickListener(v -> sendCommand("takeoff"));

        // Land
        ImageButton btnLand = findViewById(R.id.btnLand);
        btnLand.setOnClickListener(v -> sendCommand("land"));
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
     * Start MJPEG video stream from M8 drone.
     */
    private void startStreaming() {
        if (streaming) return;

        String videoUrl = config.getVideoStreamUrl();
        Log.d(TAG, "Starting video stream: " + videoUrl);

        streaming = true;
        progressBar.setVisibility(View.VISIBLE);
        lyNotConnected.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("正在连接 " + config.ip + "...");

        streamThread = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(videoUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Connection", "keep-alive");

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Stream connection failed: " + responseCode);
                    handler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        lyNotConnected.setVisibility(View.VISIBLE);
                        tvStatus.setText("连接失败: HTTP " + responseCode);
                    });
                    return;
                }

                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("已连接 " + config.ip);
                });

                String contentType = connection.getContentType();
                String boundary = extractBoundary(contentType);
                Log.d(TAG, "Content-Type: " + contentType + ", boundary: " + boundary);

                if (boundary == null) {
                    boundary = "--";
                }

                InputStream inputStream = connection.getInputStream();
                readMjpegStream(inputStream, boundary);

            } catch (Exception e) {
                Log.e(TAG, "Stream error", e);
                handler.post(() -> {
                    if (streaming) {
                        progressBar.setVisibility(View.GONE);
                        lyNotConnected.setVisibility(View.VISIBLE);
                        tvStatus.setText("连接失败: " + e.getMessage());
                    }
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                streaming = false;
            }
        });
        streamThread.start();
    }

    /**
     * Read MJPEG stream and decode frames.
     */
    private void readMjpegStream(InputStream inputStream, String boundary) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            while (streaming) {
                String line;
                int contentLength = -1;

                // Read headers until empty line
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) break;
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }

                if (contentLength <= 0 || !streaming) continue;

                // Read JPEG data
                byte[] jpegData = new byte[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength && streaming) {
                    int read = inputStream.read(jpegData, totalRead, contentLength - totalRead);
                    if (read < 0) break;
                    totalRead += read;
                }

                if (totalRead == contentLength && streaming) {
                    final Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, totalRead);
                    if (bitmap != null) {
                        handler.post(() -> {
                            if (streaming && ivVideo != null) {
                                ivVideo.setImageBitmap(bitmap);
                                frameCount++;
                                long now = System.currentTimeMillis();
                                if (now - lastFpsTime >= 1000) {
                                    int fps = (int) (frameCount * 1000.0 / (now - lastFpsTime));
                                    tvStatus.setText("已连接 " + config.ip + " | " + fps + " FPS");
                                    frameCount = 0;
                                    lastFpsTime = now;
                                }
                            }
                        });
                    }
                }

                // Read boundary separator
                while ((line = reader.readLine()) != null) {
                    if (line.contains(boundary) || line.startsWith("--")) break;
                }
            }
        } catch (Exception e) {
            if (streaming) {
                Log.e(TAG, "MJPEG read error", e);
            }
        }
    }

    /**
     * Extract boundary from Content-Type header.
     */
    private String extractBoundary(String contentType) {
        if (contentType == null) return null;
        String[] parts = contentType.split("boundary=");
        if (parts.length > 1) {
            return "--" + parts[1].trim();
        }
        return null;
    }

    /**
     * Stop the video stream.
     */
    private void stopStreaming() {
        streaming = false;
        if (streamThread != null) {
            streamThread.interrupt();
            streamThread = null;
        }
        handler.post(() -> {
            progressBar.setVisibility(View.GONE);
        });
    }

    /**
     * Take a snapshot from the drone camera.
     */
    private void takeSnapshot() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
            return;
        }

        new Thread(() -> {
            try {
                String snapshotUrl = config.getSnapshotUrl();
                URL url = new URL(snapshotUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();

                    if (bitmap != null) {
                        String filename = "M8_" + System.currentTimeMillis() + ".jpg";
                        android.provider.MediaStore.Images.Media.insertImage(
                                getContentResolver(), bitmap, filename, "M8 drone photo");
                        handler.post(() -> Toast.makeText(this, "照片已保存", Toast.LENGTH_SHORT).show());
                        bitmap.recycle();
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "拍照失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * Send a control command to the M8 drone via HTTP GET.
     */
    private void sendCommand(String cmd) {
        String commandUrl = config.getCommandUrl(cmd);
        Log.d(TAG, "Sending command: " + commandUrl);

        new Thread(() -> {
            try {
                URL url = new URL(commandUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setRequestMethod("GET");
                conn.getResponseCode();
                conn.disconnect();

                handler.post(() -> {
                    String label = getCommandLabel(cmd);
                    Toast.makeText(this, label + " 已发送", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    Toast.makeText(this, "指令发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
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
