package com.aicontrol.android.m8.activity;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.aicontrol.android.R;
import com.aicontrol.android.m8.utils.M8Config;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M8/H8 Drone video stream - v0.0.92
 * Focus: TCP:7070 video probe after discovery
 */
public class M8PlayActivity extends com.aicontrol.android.base.BaseActivity {

    private static final String TAG = "M8PlayActivity";
    private static final int STORAGE_PERMISSION_CODE = 1001;
    private static final int MAX_TCP_BUF = 204800;

    private static final int NAL_TYPE_SPS = 7;
    private static final int NAL_TYPE_PPS = 8;
    private static final int NAL_TYPE_IDR = 5;
    private static final int NAL_TYPE_SEI = 6;
    private static final int RTP_HEADER_MIN_SIZE = 12;
    private static final int RTP_NAL_STAP_A = 24;
    private static final int RTP_NAL_FU_A = 28;

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private ProgressBar progressBar;
    private LinearLayout lyNotConnected;
    private LinearLayout lyLogPanel;
    private ScrollView svLogScroll;
    private TextView tvLogContent;
    private TextView tvLogCount;
    private TextView tvStatus;
    private View btnConnect;
    private Handler handler = new Handler(Looper.getMainLooper());
    private SimpleDateFormat logTimeFmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    private volatile boolean streaming = false;
    private volatile boolean dataReceived = false;
    private Thread streamThread;
    private Thread tcpThread;
    private Socket tcpSocket;
    private DatagramSocket udpSocket;
    private M8Config.Config config;

    private MediaCodec decoder;
    private boolean decoderConfigured = false;
    private byte[] spsData = null;
    private byte[] ppsData = null;
    private BlockingQueue<byte[]> nalQueue = new LinkedBlockingQueue<>(60);

    private int frameCount = 0;
    private int totalPackets = 0;
    private int totalBytes = 0;
    private long lastFpsTime = System.currentTimeMillis();
    private AtomicInteger logCount = new AtomicInteger(0);
    private StringBuilder logBuilder = new StringBuilder();

    private int fuSeq = -1;
    private ByteBuffer fuBuffer = null;
    private boolean fuStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_m8_play);
        hideSystemUI();
        config = M8Config.loadConfig(this);
        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Prevent double-scan: only start if not already running
        if (!streaming && (streamThread == null || !streamThread.isAlive())) {
            startStreaming();
        }
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
        releaseDecoder();
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void initViews() {
        surfaceView = findViewById(R.id.svM8Video);
        progressBar = findViewById(R.id.progressBar);
        lyNotConnected = findViewById(R.id.lyNotConnected);
        lyLogPanel = findViewById(R.id.lyLogPanel);
        svLogScroll = findViewById(R.id.svLogScroll);
        tvLogContent = findViewById(R.id.tvLogContent);
        tvLogCount = findViewById(R.id.tvLogCount);
        tvStatus = findViewById(R.id.tvStatus);
        btnConnect = findViewById(R.id.btnConnect);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) { appendLog("I", "Surface created"); }
            @Override public void surfaceChanged(SurfaceHolder sh, int f, int w, int h) { appendLog("I", "Surface: " + w + "x" + h); }
            @Override public void surfaceDestroyed(SurfaceHolder h) { appendLog("I", "Surface destroyed"); }
        });

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        ImageButton btnLog = findViewById(R.id.btnLog);
        btnLog.setOnClickListener(v -> { int vis = lyLogPanel.getVisibility(); lyLogPanel.setVisibility(vis == View.GONE ? View.VISIBLE : View.GONE); });
        ImageButton btnConfig = findViewById(R.id.btnConfig);
        btnConfig.setOnClickListener(v -> { stopStreaming(); startActivity(new Intent(this, M8ConfigActivity.class)); });

        TextView btnClearLog = findViewById(R.id.btnClearLog);
        btnClearLog.setOnClickListener(v -> { logBuilder.setLength(0); logCount.set(0); tvLogContent.setText(""); tvLogCount.setText("0"); });
        TextView btnExportLog = findViewById(R.id.btnExportLog);
        btnExportLog.setOnClickListener(v -> exportLogToClipboard());
        TextView btnCopyLog = findViewById(R.id.btnCopyLog);
        btnCopyLog.setOnClickListener(v -> copyLogToClipboard());

        com.aicontrol.android.widget.KButton btnGoConfig = findViewById(R.id.btnGoConfig);
        btnGoConfig.setOnClickListener(v -> startActivity(new Intent(this, M8ConfigActivity.class)));
        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> { stopStreaming(); handler.postDelayed(() -> startStreaming(), 500); });
        }

        ImageButton btnSnap = findViewById(R.id.btnSnap);
        btnSnap.setOnClickListener(v -> takeSnapshot());
        ImageButton btnSpeed = findViewById(R.id.btnSpeed);
        btnSpeed.setOnClickListener(v -> sendTcpCommand("speed"));
        ImageButton btnEmergency = findViewById(R.id.btnEmergency);
        btnEmergency.setOnClickListener(v -> sendTcpCommand("emergency"));
        ImageButton btnTakeOff = findViewById(R.id.btnTakeOff);
        btnTakeOff.setOnClickListener(v -> sendTcpCommand("takeoff"));
        ImageButton btnLand = findViewById(R.id.btnLand);
        btnLand.setOnClickListener(v -> sendTcpCommand("land"));
    }

    // =========================================================================
    // Export & Copy Log
    // =========================================================================

    private void exportLogToClipboard() {
        String logText = logBuilder.toString();
        if (logText.isEmpty()) { Toast.makeText(this, "日志为空", Toast.LENGTH_SHORT).show(); return; }
        String export = "=== AiControl M8 Debug Log ===\n"
                + "Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n"
                + "Config: IP=" + config.ip + " UDP=" + config.udpPort + " TCP=" + config.tcpPort + "\n"
                + "================================\n" + logText;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("M8_DebugLog", export));
        appendLog("I", "日志已导出 (" + export.length() + "字符)");
        Toast.makeText(this, "日志已导出到剪贴板", Toast.LENGTH_SHORT).show();
    }

    private void copyLogToClipboard() {
        String t = logBuilder.toString();
        if (t.isEmpty()) { Toast.makeText(this, "日志为空", Toast.LENGTH_SHORT).show(); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("M8_Log", t));
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    // =========================================================================
    // Network Diagnostics
    // =========================================================================

    private void logNetworkDiagnostics() {
        appendLog("I", "===== 网络诊断 =====");
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            appendLog("I", "[WiFi] SSID=" + wi.getSSID() + " RSSI=" + wi.getRssi() + "dBm Speed=" + wi.getLinkSpeed() + "Mbps IP=" + intToIp(wi.getIpAddress()));
            DhcpInfo dhcp = wm.getDhcpInfo();
            appendLog("I", "[DHCP] GW=" + intToIp(dhcp.gateway) + " Mask=" + intToIp(dhcp.netmask));
        } catch (Exception e) { appendLog("W", "[WiFi] " + e.getMessage()); }
    }

    private static String intToIp(int a) { return ((a&0xFF)+"."+((a>>8)&0xFF)+"."+((a>>16)&0xFF)+"."+((a>>24)&0xFF)); }

    // =========================================================================
    // Log
    // =========================================================================

    private void appendLog(String level, String msg) {
        String time = logTimeFmt.format(new Date());
        String line = "[" + time + "] " + level + " " + msg + "\n";
        Log.d(TAG, line);
        handler.post(() -> {
            if (tvLogContent == null) return;
            logBuilder.append(line);
            String text = logBuilder.toString();
            int lc = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') lc++;
                if (lc > 800) { text = text.substring(text.indexOf('\n', i) + 1); logBuilder.setLength(0); logBuilder.append(text); break; }
            }
            tvLogContent.setText(logBuilder.toString());
            svLogScroll.post(() -> svLogScroll.fullScroll(ScrollView.FOCUS_DOWN));
            tvLogCount.setText(String.valueOf(logCount.incrementAndGet()));
        });
    }

    private static String hex(byte[] d, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len && i < 64; i++) sb.append(String.format("%02X ", d[i] & 0xFF));
        return sb.toString().trim();
    }

    // =========================================================================
    // TCP Port Scan (quick)
    // =========================================================================

    private int[] scanTcpPorts() {
        int[] ports = {4646, 7070, 80, 8080, 8554, 554, 3000, 3001, 5000, 5001, 9090, 8866, 443, 1935};
        int[] open = new int[ports.length];
        int cnt = 0;
        for (int p : ports) {
            Socket s = null;
            try {
                s = new Socket(); s.connect(new InetSocketAddress(config.ip, p), 800);
                open[cnt++] = p;
                s.setSoTimeout(300);
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    String banner = br.readLine();
                    appendLog("I", "[TCP] :" + p + " OPEN banner=" + (banner != null ? truncate(banner, 100) : "(空)"));
                } catch (Exception e) { appendLog("I", "[TCP] :" + p + " OPEN (无banner)"); }
                s.close();
            } catch (Exception e) { if (s != null) try { s.close(); } catch (Exception ig) {} }
        }
        int[] r = new int[cnt];
        System.arraycopy(open, 0, r, 0, cnt);
        appendLog("I", "[TCP-SCAN] 开放: " + java.util.Arrays.toString(r));
        return r;
    }

    private String truncate(String s, int max) { return s.length() > max ? s.substring(0, max) + "..." : s; }

    // =========================================================================
    // Probe TCP:7070 (or whatever ports we find)
    // =========================================================================

    private boolean probeTcpVideoPort(int port) {
        appendLog("I", "[PROBE] 深度探测 TCP:" + port + " ...");

        // Strategy 1: Read the 405 response headers to discover allowed methods
        appendLog("I", "[PROBE] TCP:" + port + " 读取OPTIONS/GET响应headers...");
        String[] probePaths = {"/", "/live", "/video", "/stream", "/api/v1/live"};
        for (String path : probePaths) {
            if (!streaming) return false;
            Socket s = null;
            try {
                s = new Socket(); s.connect(new InetSocketAddress(config.ip, port), 2000);
                s.setSoTimeout(2000);
                OutputStream os = s.getOutputStream();
                // Try GET first to read full response headers (especially Allow header)
                String req = "GET " + path + " HTTP/1.1\r\nHost: " + config.ip + ":" + port + "\r\nConnection: close\r\n\r\n";
                os.write(req.getBytes("UTF-8")); os.flush();

                InputStream is = s.getInputStream();
                byte[] buf = new byte[4096];
                int n = is.read(buf, 0, buf.length);
                if (n > 0) {
                    String head = new String(buf, 0, Math.min(n, 2000));
                    String[] lines = head.split("\r\n");
                    for (String line : lines) {
                        appendLog("I", "[PROBE] GET" + path + ": " + line);
                        if (line.toLowerCase().startsWith("allow:")) {
                            appendLog("I", ">>> 发现Allow头! 允许的方法: " + line);
                        }
                        if (line.toLowerCase().startsWith("server:")) {
                            appendLog("I", ">>> Server: " + line);
                        }
                        if (line.toLowerCase().startsWith("content-type:")) {
                            appendLog("I", ">>> " + line);
                        }
                        if (line.isEmpty()) break;
                    }
                }
                s.close();
            } catch (Exception e) { if (s != null) try { s.close(); } catch (Exception ig) {} }
        }

        // Strategy 1b: Try POST requests with various content types
        appendLog("I", "[PROBE] TCP:" + port + " 尝试POST请求...");
        String[][] postCmds = {
            {"/live", "application/json", "{\"CMD\":20,\"PARAM\":\"\"}"},
            {"/live", "application/json", "{\"action\":\"start\"}"},
            {"/live", "application/json", "{\"T\":\"live\",\"CMD\":\"VSTART\"}"},
            {"/video", "application/json", "{\"CMD\":20}"},
            {"/stream", "application/json", "{\"start\":true}"},
            {"/", "application/json", "{\"CMD\":0}"},
            {"/api/v1/start_live", "application/json", "{}"},
            {"/live", "application/octet-stream", "D8C0D9"},  // binary handshake as POST body
            {"/", "application/octet-stream", "D8C0D9"},
            {"/", "text/plain", "start_video"},
        };
        for (String[] pc : postCmds) {
            if (!streaming) return false;
            Socket s = null;
            try {
                s = new Socket(); s.connect(new InetSocketAddress(config.ip, port), 1500);
                s.setSoTimeout(1500);
                OutputStream os = s.getOutputStream();
                String body = pc[2];
                String req = "POST " + pc[0] + " HTTP/1.1\r\nHost: " + config.ip + ":" + port + "\r\n"
                        + "Content-Type: " + pc[1] + "\r\nContent-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body;
                os.write(req.getBytes("UTF-8")); os.flush();

                InputStream is = s.getInputStream();
                byte[] buf = new byte[4096];
                int n = is.read(buf, 0, buf.length);
                if (n > 0) {
                    String resp = new String(buf, 0, Math.min(n, 500));
                    String firstLine = resp.split("\n")[0];
                    appendLog("I", "[PROBE] POST" + pc[0] + " (" + pc[1].split(";")[0] + ") => " + firstLine + " " + n + "B");
                    if (firstLine.contains("200") || firstLine.contains("201")) {
                        appendLog("I", ">>> POST成功! 完整响应:");
                        String[] lines = resp.split("\r\n");
                        for (String line : lines) {
                            appendLog("I", "[PROBE]   " + line);
                            if (line.isEmpty()) break;
                        }
                        appendLog("I", "[PROBE] hex: " + hex(buf, Math.min(n, 64)));
                    }
                } else {
                    appendLog("D", "[PROBE] POST" + pc[0] + " 无响应");
                }
                s.close();
            } catch (Exception e) { if (s != null) try { s.close(); } catch (Exception ig) {} }
        }

        // Strategy 1c: Try WebSocket upgrade
        appendLog("I", "[PROBE] TCP:" + port + " 尝试WebSocket...");
        for (String wsPath : new String[]{"/", "/live", "/ws", "/video", "/stream"}) {
            if (!streaming) return false;
            Socket s = null;
            try {
                s = new Socket(); s.connect(new InetSocketAddress(config.ip, port), 1000);
                s.setSoTimeout(2000);
                String wsKey = java.util.Base64.getEncoder().encodeToString(("m8probe" + System.currentTimeMillis()).getBytes());
                String upgrade = "GET " + wsPath + " HTTP/1.1\r\nHost: " + config.ip + ":" + port + "\r\n"
                        + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: " + wsKey + "\r\n"
                        + "Sec-WebSocket-Version: 13\r\n\r\n";
                s.getOutputStream().write(upgrade.getBytes("UTF-8"));
                s.getOutputStream().flush();

                byte[] buf = new byte[4096];
                int n = s.getInputStream().read(buf, 0, buf.length);
                if (n > 0) {
                    String resp = new String(buf, 0, Math.min(n, 500));
                    appendLog("I", "[PROBE] WS" + wsPath + ": " + resp.split("\n")[0]);
                    if (resp.contains("101") || resp.contains("Switching")) {
                        appendLog("I", ">>> WebSocket升级成功! " + wsPath);
                        // Read websocket frames
                        s.close(); return true;
                    }
                }
                s.close();
            } catch (Exception e) { if (s != null) try { s.close(); } catch (Exception ig) {} }
        }

        // Strategy 1d: Try PUT, DELETE, custom methods
        appendLog("I", "[PROBE] TCP:" + port + " 尝试其他HTTP方法...");
        for (String method : new String[]{"POST", "PUT", "OPTIONS", "HEAD"}) {
            if (!streaming) return false;
            Socket s = null;
            try {
                s = new Socket(); s.connect(new InetSocketAddress(config.ip, port), 1000);
                s.setSoTimeout(1500);
                String req2 = method + " /live HTTP/1.1\r\nHost: " + config.ip + ":" + port + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                s.getOutputStream().write(req2.getBytes("UTF-8")); s.getOutputStream().flush();
                byte[] buf = new byte[4096];
                int n = s.getInputStream().read(buf, 0, buf.length);
                if (n > 0) {
                    String resp = new String(buf, 0, Math.min(n, 500));
                    appendLog("I", "[PROBE] " + method + " /live => " + resp.split("\n")[0]);
                    // Print all headers
                    String[] lines = resp.split("\r\n");
                    for (String line : lines) {
                        appendLog("I", "[PROBE]   " + line);
                        if (line.isEmpty()) break;
                    }
                }
                s.close();
            } catch (Exception e) { if (s != null) try { s.close(); } catch (Exception ig) {} }
        }

        // Strategy 1e: Try HTTP with binary body (handshake as HTTP body)
        appendLog("I", "[PROBE] TCP:" + port + " 尝试HTTP+二进制体...");
        Socket s = null;
        try {
            s = new Socket(); s.connect(new InetSocketAddress(config.ip, port), 2000);
            s.setSoTimeout(3000);
            byte[] handshakeBody = {(byte)0xD8, (byte)0xC0, (byte)0xD9};
            String req3 = "POST / HTTP/1.1\r\nHost: " + config.ip + ":" + port + "\r\n"
                    + "Content-Type: application/octet-stream\r\nContent-Length: 3\r\nConnection: close\r\n\r\n";
            OutputStream os = s.getOutputStream();
            os.write(req3.getBytes("UTF-8"));
            os.write(handshakeBody);
            os.flush();

            // Wait for potential streaming response
            byte[] bigBuf = new byte[32768];
            int total = 0;
            try {
                while (streaming) {
                    int n = s.getInputStream().read(bigBuf, total, bigBuf.length - total);
                    if (n <= 0) break;
                    total += n;
                    if (total > 100) break; // Got enough
                }
            } catch (Exception e) {}
            if (total > 0) {
                appendLog("I", "[PROBE] HTTP+二进制 响应 " + total + "B:");
                String head = new String(bigBuf, 0, Math.min(total, 500));
                String[] lines = head.split("\r\n");
                for (String line : lines) {
                    appendLog("I", "[PROBE]   " + line);
                    if (line.isEmpty()) break;
                }
                int bodyOff = findHttpBodyStart(bigBuf, total);
                if (bodyOff > 0 && total > bodyOff) {
                    appendLog("I", "[PROBE] Body hex: " + hex(java.util.Arrays.copyOfRange(bigBuf, bodyOff, total), Math.min(total - bodyOff, 64)));
                }
            }
            s.close();
        } catch (Exception e) { if (s != null) try { s.close(); } catch (Exception ig) {} }

        // Strategy 2: Try sending video activation JSON commands to this port
        appendLog("I", "[PROBE] TCP:" + port + " 尝试JSON激活命令...");
        String[] cmds = {
            "{\"CMD\":0}",
            "{\"CMD\":20,\"PARAM\":\"\"}",
            "{\"T\":\"live\"}",
            "{\"action\":\"start\",\"type\":\"video\"}",
            "{\"msg\":\"video_start\"}",
        };
        for (String cmd : cmds) {
            if (!streaming) return false;
            Socket s2 = null;
            try {
                s2 = new Socket(); s2.connect(new InetSocketAddress(config.ip, port), 1000);
                s2.setSoTimeout(1000);
                OutputStream os2 = s2.getOutputStream();
                os2.write((cmd + "\n").getBytes("UTF-8"));
                os2.flush();
                appendLog("D", "[PROBE] TCP:" + port + " sent: " + cmd);
                try {
                    BufferedReader br2 = new BufferedReader(new InputStreamReader(s2.getInputStream()));
                    String resp = br2.readLine();
                    if (resp != null) appendLog("I", "[PROBE] TCP:" + port + " resp: " + truncate(resp, 200));
                } catch (Exception e) {}
                s2.close();
            } catch (Exception e) { if (s2 != null) try { s2.close(); } catch (Exception ig) {} }
        }

        // Strategy 3: Raw binary handshake on this TCP port
        appendLog("I", "[PROBE] TCP:" + port + " 尝试二进制握手...");
        byte[][] handshakes = {
            {(byte)0xD8, (byte)0xC0, (byte)0xD9},
            {0x00, (byte)0xD8, (byte)0xC0, (byte)0xD9},
            "GET / HTTP/1.1\r\n\r\n".getBytes(),
            "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\n\r\n".getBytes(),
        };
        for (byte[] hs : handshakes) {
            if (!streaming) return false;
            Socket s3 = null;
            try {
                s3 = new Socket(); s3.connect(new InetSocketAddress(config.ip, port), 1000);
                s3.setSoTimeout(2000);
                OutputStream os3 = s3.getOutputStream();
                os3.write(hs);
                os3.flush();
                appendLog("D", "[PROBE] TCP:" + port + " sent " + hs.length + "B: " + hex(hs, hs.length));
                byte[] rbuf3 = new byte[1024];
                try {
                    int n = s3.getInputStream().read(rbuf3, 0, rbuf3.length);
                    if (n > 0) {
                        appendLog("I", "[PROBE] TCP:" + port + " GOT " + n + "B! hex: " + hex(rbuf3, n));
                        appendLog("I", "[PROBE] >>> 此端口有响应! 可能是视频端口!");
                        s3.close();
                        return true;
                    }
                } catch (Exception e) {}
                s3.close();
            } catch (Exception e) { if (s3 != null) try { s3.close(); } catch (Exception ig) {} }
        }

        // Strategy 4: Connect and just wait for data (server might push)
        appendLog("I", "[PROBE] TCP:" + port + " 等待服务器推送...");
        Socket s4 = null;
        try {
            s4 = new Socket(); s4.connect(new InetSocketAddress(config.ip, port), 2000);
            s4.setSoTimeout(3000);
            appendLog("I", "[PROBE] TCP:" + port + " 已连接, 等待3秒...");
            byte[] rbuf4 = new byte[MAX_TCP_BUF];
            try {
                int n = s4.getInputStream().read(rbuf4, 0, rbuf4.length);
                if (n > 0) {
                    appendLog("I", "[PROBE] TCP:" + port + " 推送 " + n + "B! hex: " + hex(rbuf4, n));
                    s4.close();
                    return true;
                }
            } catch (Exception e) {}
            s4.close();
        } catch (Exception e) { if (s4 != null) try { s4.close(); } catch (Exception ig) {} }

        return false;
    }

    private int findHttpBodyStart(byte[] buf, int len) {
        for (int i = 0; i < len - 3; i++) {
            if (buf[i] == '\r' && buf[i+1] == '\n' && buf[i+2] == '\r' && buf[i+3] == '\n') return i + 4;
        }
        return -1;
    }

    // =========================================================================
    // TCP:4646 with proper command exchange
    // =========================================================================

    private Socket connectTcp4646() {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(config.ip, config.tcpPort), 3000);
            s.setSoTimeout(3000);
            s.setKeepAlive(true);
            s.setTcpNoDelay(true);
            appendLog("I", "[TCP:4646] 已连接");

            // Read initial data (server may push JSON immediately)
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            s.setSoTimeout(3000);
            try {
                String line = br.readLine();
                if (line != null) {
                    appendLog("I", "[TCP:4646] 初始: " + truncate(line, 200));
                    try {
                        JSONObject j = new JSONObject(line);
                        appendLog("I", "[TCP:4646] JSON: " + j.toString());
                    } catch (Exception ignored) {}
                }
            } catch (SocketTimeoutException e) {
                appendLog("I", "[TCP:4646] 无初始数据, 发送查询...");
                // Send query to wake up connection
                OutputStream os = s.getOutputStream();
                JSONObject q = new JSONObject(); q.put("CMD", 0); q.put("PARAM", "");
                os.write((q.toString() + "\n").getBytes("UTF-8"));
                os.flush();
                s.setSoTimeout(2000);
                try {
                    String resp = br.readLine();
                    if (resp != null) appendLog("I", "[TCP:4646] 查询响应: " + truncate(resp, 200));
                } catch (Exception e2) { appendLog("D", "[TCP:4646] 查询无响应"); }
            }

            // Keep reading in background
            s.setSoTimeout(0);
            tcpSocket = s;
            tcpThread = new Thread(() -> {
                try {
                    String line;
                    while (streaming && (line = br.readLine()) != null) {
                        appendLog("D", "[TCP:4646] " + truncate(line, 300));
                    }
                } catch (Exception e) { if (streaming) appendLog("W", "[TCP:4646] 断开: " + e.getMessage()); }
            }, "TCP4646");
            tcpThread.start();
        } catch (Exception e) {
            appendLog("W", "[TCP:4646] 连接失败: " + e.getMessage());
        }
        return s;
    }

    // =========================================================================
    // Main streaming flow
    // =========================================================================

    private void startStreaming() {
        if (streaming) return;
        streaming = true;
        dataReceived = false;
        nalQueue.clear();
        fuStarted = false; fuBuffer = null; fuSeq = -1;
        spsData = null; ppsData = null;
        frameCount = 0; totalPackets = 0; totalBytes = 0;

        progressBar.setVisibility(View.VISIBLE);
        lyNotConnected.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("扫描中...");
        if (btnConnect != null) btnConnect.setVisibility(View.GONE);

        appendLog("I", "========== 开始连接 (v0.0.92-7070probe) ==========");
        logNetworkDiagnostics();

        streamThread = new Thread(() -> {
            try {
                // Phase 1: TCP scan
                appendLog("I", "[PHASE1] TCP端口扫描...");
                int[] tcpPorts = scanTcpPorts();

                // Phase 2: Connect TCP:4646 for control
                appendLog("I", "[PHASE2] 连接TCP:4646...");
                connectTcp4646();
                Thread.sleep(500);

                // Phase 3: Probe each open TCP port (especially 7070)
                for (int port : tcpPorts) {
                    if (!streaming) break;
                    if (port == config.tcpPort) continue; // Already connected
                    appendLog("I", "[PHASE3] 探测 TCP:" + port + "...");
                    boolean gotResponse = probeTcpVideoPort(port);
                    if (gotResponse) {
                        appendLog("I", "[PHASE3] TCP:" + port + " 有响应! 标记为候选视频端口");
                    }
                    Thread.sleep(300);
                }

                // Phase 4: Try to establish video stream on most likely port
                appendLog("I", "[PHASE4] 建立视频流连接...");
                boolean videoOk = false;

                // Try TCP streaming on discovered ports
                for (int port : tcpPorts) {
                    if (!streaming) break;
                    if (port == config.tcpPort) continue;
                    appendLog("I", "[PHASE4] 尝试TCP视频流 :" + port + "...");
                    videoOk = tryTcpVideoStream(port);
                    if (videoOk) {
                        appendLog("I", ">>> 视频流已建立 on TCP:" + port);
                        break;
                    }
                }

                if (!videoOk) {
                    // Also try configured UDP port
                    appendLog("I", "[PHASE4] 尝试UDP视频流 :" + config.udpPort + "...");
                    videoOk = tryUdpVideoStream(config.udpPort);
                }

                if (!videoOk && streaming) {
                    appendLog("E", "未建立视频连接");
                    handler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        lyNotConnected.setVisibility(View.VISIBLE);
                        tvStatus.setText("未收到视频数据");
                        if (btnConnect != null) btnConnect.setVisibility(View.VISIBLE);
                    });
                }

            } catch (Exception e) {
                appendLog("E", "异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                streaming = false;
                appendLog("I", "========== 结束 (包=" + totalPackets + " 字节=" + totalBytes + ") ==========");
            }
        }, "StreamThread");
        streamThread.start();
    }

    // =========================================================================
    // Try TCP video stream (raw socket read)
    // =========================================================================

    private boolean tryTcpVideoStream(int port) {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(config.ip, port), 3000);
            s.setSoTimeout(5000);
            s.setReceiveBufferSize(MAX_TCP_BUF);
            appendLog("I", "[TCP-VIDEO] :" + port + " 已连接 bufsize=" + s.getReceiveBufferSize());

            // Send handshake
            OutputStream os = s.getOutputStream();
            byte[] handshake = {(byte)0xD8, (byte)0xC0, (byte)0xD9};
            os.write(handshake);
            os.flush();
            appendLog("I", "[TCP-VIDEO] 握手已发");

            // Start decoder
            Thread dt = new Thread(this::decoderLoop, "DecodeThread");
            dt.start();

            handler.post(() -> tvStatus.setText("TCP:" + port + " 等待视频..."));

            // Read loop
            byte[] buf = new byte[MAX_TCP_BUF];
            int timeouts = 0;

            while (streaming) {
                try {
                    int n = s.getInputStream().read(buf, 0, buf.length);
                    if (n <= 0) { appendLog("W", "[TCP-VIDEO] EOF"); break; }
                    totalPackets++;
                    totalBytes += n;
                    timeouts = 0;

                    if (!dataReceived) {
                        dataReceived = true;
                        handler.post(() -> progressBar.setVisibility(View.GONE));
                        appendLog("I", ">>> TCP首数据! port=" + port + " len=" + n);
                    }

                    if (totalPackets <= 20) {
                        appendLog("D", "[TCP-VIDEO] #" + totalPackets + " len=" + n + " [" + hex(buf, Math.min(n, 64)) + "]");
                    }

                    // Parse data
                    parseTcpData(buf, n);

                    if (totalPackets % 100 == 0) {
                        long now = System.currentTimeMillis();
                        double rate = totalBytes * 1000.0 / Math.max(1, now - lastFpsTime);
                        handler.post(() -> tvStatus.setText("TCP:" + port + " " + String.format("%.0f", rate / 1024) + " KB/s"));
                        appendLog("I", "[STATS] " + totalPackets + " pkts " + String.format("%.0f", rate) + " B/s");
                    }

                } catch (SocketTimeoutException e) {
                    timeouts++;
                    appendLog("D", "[TCP-VIDEO] 超时#" + timeouts);
                    if (!dataReceived && timeouts >= 3) {
                        appendLog("I", "[TCP-VIDEO] 无数据, 切换端口");
                        break;
                    }
                    // Resend handshake
                    try { os.write(handshake); os.flush(); } catch (Exception ig) {}
                }
            }
            s.close();
            return dataReceived;
        } catch (Exception e) {
            appendLog("W", "[TCP-VIDEO] :" + port + " " + e.getMessage());
            if (s != null) try { s.close(); } catch (Exception ig) {}
            return false;
        }
    }

    // =========================================================================
    // Parse TCP data (could be HTTP/MJPEG, raw H264, RTP over TCP, etc.)
    // =========================================================================

    private void parseTcpData(byte[] buf, int len) {
        // Check for H264 start codes
        int nalStart = findH264Start(buf, len);
        if (nalStart >= 0) {
            appendLog("I", "[TCP-DATA] 检测到H264 start code at offset " + nalStart);
            // Extract NAL units
            parseH264Stream(buf, len);
            return;
        }

        // Check for MJPEG (FF D8)
        for (int i = 0; i < len - 1; i++) {
            if ((buf[i] & 0xFF) == 0xFF && (buf[i+1] & 0xFF) == 0xD8) {
                appendLog("I", "[TCP-DATA] 检测到JPEG at offset " + i + " - MJPEG流!");
                // For now just log it, would need different decoder
                return;
            }
        }

        // Check if it looks like RTP
        if (len >= RTP_HEADER_MIN_SIZE && ((buf[0] >> 6) & 0x03) == 2) {
            if (totalPackets <= 5) appendLog("D", "[TCP-DATA] 可能是RTP over TCP");
            parseRtpPacket(buf, len);
            return;
        }

        // Check if HTTP response
        if (len >= 4 && buf[0] == 'H' && buf[1] == 'T' && buf[2] == 'T' && buf[3] == 'P') {
            String header = new String(buf, 0, Math.min(len, 500));
            String[] lines = header.split("\r\n");
            for (String line : lines) {
                if (totalPackets <= 5) appendLog("D", "[TCP-DATA] HTTP: " + line);
                if (line.isEmpty()) break;
            }
            int bodyStart = findHttpBodyStart(buf, len);
            if (bodyStart > 0 && bodyStart < len) {
                appendLog("I", "[TCP-DATA] HTTP body at " + bodyStart + " len=" + (len - bodyStart));
                parseTcpData(java.util.Arrays.copyOfRange(buf, bodyStart, len), len - bodyStart);
            }
            return;
        }

        if (totalPackets <= 10) {
            appendLog("D", "[TCP-DATA] 未知格式 first=" + hex(buf, Math.min(len, 32)));
        }
    }

    private int findH264Start(byte[] buf, int len) {
        for (int i = 0; i < len - 3; i++) {
            if (buf[i] == 0 && buf[i+1] == 0 && (buf[i+2] == 1 || (buf[i+2] == 0 && i + 3 < len && buf[i+3] == 1))) {
                return i;
            }
        }
        return -1;
    }

    private void parseH264Stream(byte[] buf, int len) {
        // Find NAL units by start codes (00 00 00 01 or 00 00 01)
        int i = 0;
        while (i < len - 3) {
            int start = -1;
            if (buf[i] == 0 && buf[i+1] == 0 && buf[i+2] == 0 && buf[i+3] == 1) start = i + 4;
            else if (buf[i] == 0 && buf[i+1] == 0 && buf[i+2] == 1) start = i + 3;

            if (start >= 0 && start < len) {
                // Find end
                int end = len;
                for (int j = start + 1; j < len - 3; j++) {
                    if (buf[j] == 0 && buf[j+1] == 0 && (buf[j+2] == 1 || (buf[j+2] == 0 && buf[j+3] == 1))) {
                        end = j; break;
                    }
                }
                byte[] nal = new byte[end - start];
                System.arraycopy(buf, start, nal, 0, nal.length);

                if (nal.length > 0) {
                    int nalType = (nal[0] & 0xFF) & 0x1F;
                    if (totalPackets <= 20) {
                        appendLog("D", "[H264] NAL type=" + nalType + " size=" + nal.length);
                    }
                    handleNalUnit(nal);
                }
                i = start + 1;
            } else {
                i++;
            }
        }
    }

    // =========================================================================
    // Try UDP video stream
    // =========================================================================

    private boolean tryUdpVideoStream(int port) {
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket(null);
            ds.setReuseAddress(true);
            ds.bind(new InetSocketAddress(0));
            ds.setSoTimeout(3000);
            ds.connect(new InetSocketAddress(config.ip, port));
            appendLog("I", "[UDP-VIDEO] :" + port + " 本地=" + ds.getLocalPort());

            byte[] hs = {(byte)0xD8, (byte)0xC0, (byte)0xD9};
            DatagramPacket hpkt = new DatagramPacket(hs, hs.length);
            ds.send(hpkt);
            appendLog("I", "[UDP-VIDEO] 握手已发");

            Thread dt = new Thread(this::decoderLoop, "DecodeThread");
            dt.start();

            byte[] buf = new byte[MAX_TCP_BUF];
            int timeouts = 0;

            while (streaming) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    ds.receive(pkt);
                    int len = pkt.getLength();
                    totalPackets++; totalBytes += len; timeouts = 0;

                    if (!dataReceived) {
                        dataReceived = true;
                        handler.post(() -> progressBar.setVisibility(View.GONE));
                        appendLog("I", ">>> UDP首包! port=" + port + " len=" + len);
                    }
                    if (totalPackets <= 20) appendLog("D", "[UDP] #" + totalPackets + " len=" + len + " [" + hex(buf, Math.min(len, 64)) + "]");

                    if (len >= RTP_HEADER_MIN_SIZE && ((buf[0] >> 6) & 0x03) == 2) {
                        parseRtpPacket(buf, len);
                    } else if (findH264Start(buf, len) >= 0) {
                        parseH264Stream(buf, len);
                    }

                } catch (SocketTimeoutException e) {
                    timeouts++;
                    if (!dataReceived && timeouts >= 2) { appendLog("I", "[UDP-VIDEO] 无数据"); return false; }
                    if (dataReceived && timeouts >= 5) { appendLog("W", "[UDP-VIDEO] 数据中断"); break; }
                } catch (PortUnreachableException e) {
                    appendLog("W", "[UDP-VIDEO] :" + port + " ICMP不可达");
                    return false;
                }
            }
            return dataReceived;
        } catch (Exception e) {
            appendLog("W", "[UDP-VIDEO] :" + port + " " + e.getMessage());
            return false;
        } finally { if (ds != null) try { ds.close(); } catch (Exception ig) {} }
    }

    // =========================================================================
    // RTP + NAL parsing (same as before)
    // =========================================================================

    private void parseRtpPacket(byte[] data, int length) {
        int version = (data[0] >> 6) & 0x03;
        if (version != 2) return;
        int padding = (data[0] >> 5) & 0x01;
        int extension = (data[0] >> 4) & 0x01;
        int csrcCount = data[0] & 0x0F;
        int marker = (data[1] >> 7) & 0x01;
        int payloadType = data[1] & 0x7F;
        int seqNum = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

        int headerSize = RTP_HEADER_MIN_SIZE + (csrcCount * 4);
        if (extension == 1 && length > headerSize + 4) {
            int extLen = ((data[headerSize+2] & 0xFF) << 8) | (data[headerSize+3] & 0xFF);
            headerSize += 4 + (extLen * 4);
        }
        if (headerSize >= length) return;
        int payloadOffset = headerSize;
        int payloadLength = length - headerSize;
        if (padding == 1 && payloadLength > 0) payloadLength -= (data[length-1] & 0xFF);
        if (payloadLength <= 0) return;

        if (totalPackets <= 5) appendLog("D", String.format("RTP: PT=%d Seq=%d M=%d Pay=%d", payloadType, seqNum, marker, payloadLength));

        int nalHeaderByte = data[payloadOffset] & 0xFF;
        int nalType = nalHeaderByte & 0x1F;

        if (nalType >= 1 && nalType <= 23) {
            byte[] nal = new byte[payloadLength + 4];
            nal[0] = 0; nal[1] = 0; nal[2] = 0; nal[3] = 1;
            System.arraycopy(data, payloadOffset, nal, 4, payloadLength);
            handleNalUnit(nal);
        } else if (nalType == RTP_NAL_STAP_A) {
            parseStapA(data, payloadOffset, payloadLength, marker);
        } else if (nalType == RTP_NAL_FU_A) {
            parseFuA(data, payloadOffset, payloadLength, seqNum, marker);
        }
    }

    private void parseStapA(byte[] data, int offset, int length, int marker) {
        int pos = offset + 1;
        while (pos + 2 < offset + length) {
            int nalSize = ((data[pos] & 0xFF) << 8) | (data[pos+1] & 0xFF);
            pos += 2;
            if (nalSize <= 0 || pos + nalSize > offset + length) break;
            byte[] nal = new byte[nalSize + 4];
            nal[0] = 0; nal[1] = 0; nal[2] = 0; nal[3] = 1;
            System.arraycopy(data, pos, nal, 4, nalSize);
            handleNalUnit(nal);
            pos += nalSize;
        }
    }

    private void parseFuA(byte[] data, int offset, int length, int seqNum, int marker) {
        if (length < 2) return;
        int fuIndicator = data[offset] & 0xFF;
        int fuHeader = data[offset+1] & 0xFF;
        boolean startBit = ((fuHeader >> 7) & 0x01) == 1;
        boolean endBit = ((fuHeader >> 6) & 0x01) == 1;
        int nalType = fuHeader & 0x1F;
        int nalRefIdc = (fuIndicator >> 5) & 0x03;
        int fuPayloadOffset = offset + 2;
        int fuPayloadLength = length - 2;

        if (startBit) {
            fuStarted = true; fuSeq = seqNum;
            byte origHeader = (byte) ((nalRefIdc << 5) | nalType);
            fuBuffer = ByteBuffer.allocate(4 + 1 + fuPayloadLength + 60000);
            fuBuffer.put(new byte[]{0, 0, 0, 1});
            fuBuffer.put(origHeader);
            fuBuffer.put(data, fuPayloadOffset, fuPayloadLength);
        } else if (fuStarted) {
            if (fuBuffer != null && fuBuffer.remaining() >= fuPayloadLength) {
                fuBuffer.put(data, fuPayloadOffset, fuPayloadLength);
            } else if (fuBuffer != null) {
                ByteBuffer nb = ByteBuffer.allocate(fuBuffer.position() + fuPayloadLength + 60000);
                fuBuffer.flip(); nb.put(fuBuffer);
                nb.put(data, fuPayloadOffset, fuPayloadLength);
                fuBuffer = nb;
            }
            if (endBit) {
                fuStarted = false;
                if (fuBuffer != null) {
                    byte[] nal = new byte[fuBuffer.position()];
                    fuBuffer.flip(); fuBuffer.get(nal); fuBuffer = null;
                    handleNalUnit(nal);
                }
            }
        }
    }

    // =========================================================================
    // NAL handling + MediaCodec
    // =========================================================================

    private void handleNalUnit(byte[] nal) {
        if (nal.length < 5) return;
        // nal might have 00 00 00 01 prefix or not
        int dataOff = 0;
        if (nal.length >= 4 && nal[0] == 0 && nal[1] == 0 && nal[2] == 0 && nal[3] == 1) dataOff = 4;
        else if (nal.length >= 3 && nal[0] == 0 && nal[1] == 0 && nal[2] == 1) dataOff = 3;

        if (nal.length - dataOff < 1) return;
        int nalType = (nal[dataOff] & 0xFF) & 0x1F;

        if (nalType == NAL_TYPE_SPS) {
            spsData = new byte[nal.length - dataOff];
            System.arraycopy(nal, dataOff, spsData, 0, spsData.length);
            appendLog("I", ">>> SPS! size=" + spsData.length + " [" + hex(spsData, Math.min(spsData.length, 32)) + "]");
            tryConfigureDecoder();
            return;
        }
        if (nalType == NAL_TYPE_PPS) {
            ppsData = new byte[nal.length - dataOff];
            System.arraycopy(nal, dataOff, ppsData, 0, ppsData.length);
            appendLog("I", ">>> PPS! size=" + ppsData.length + " [" + hex(ppsData, Math.min(ppsData.length, 16)) + "]");
            tryConfigureDecoder();
            return;
        }
        if (nalType == NAL_TYPE_SEI) return;

        if (decoderConfigured) {
            if (nalQueue.remainingCapacity() == 0) nalQueue.poll();
            nalQueue.offer(nal);
        }
    }

    private void tryConfigureDecoder() {
        if (decoderConfigured || spsData == null || ppsData == null) return;
        handler.post(() -> {
            if (decoderConfigured) return;
            int[][] res = {{1280,720},{960,720},{856,480},{640,480},{1920,1080}};
            for (int[] r : res) {
                try {
                    MediaFormat fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, r[0], r[1]);
                    fmt.setByteBuffer("csd-0", ByteBuffer.wrap(spsData));
                    fmt.setByteBuffer("csd-1", ByteBuffer.wrap(ppsData));
                    fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_TCP_BUF);
                    decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                    decoder.configure(fmt, surfaceHolder.getSurface(), null, 0);
                    decoder.start();
                    decoderConfigured = true;
                    appendLog("I", ">>> MediaCodec! " + r[0] + "x" + r[1]);
                    return;
                } catch (Exception e) { if (decoder != null) { try { decoder.release(); } catch (Exception ig) {} decoder = null; } }
            }
        });
    }

    private void decoderLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (streaming || !nalQueue.isEmpty()) {
            try {
                byte[] nal = nalQueue.poll();
                if (nal == null) { Thread.sleep(5); continue; }
                if (!decoderConfigured || decoder == null) continue;

                int inIdx = decoder.dequeueInputBuffer(10000);
                if (inIdx >= 0) {
                    ByteBuffer inBuf = decoder.getInputBuffer(inIdx);
                    if (inBuf != null) {
                        inBuf.clear(); inBuf.put(nal);
                        int flags = 0;
                        // Find NAL type
                        int off = (nal.length >= 4 && nal[0]==0 && nal[1]==0 && nal[2]==0 && nal[3]==1) ? 4 :
                                  (nal.length >= 3 && nal[0]==0 && nal[1]==0 && nal[2]==1) ? 3 : 0;
                        if (off < nal.length) { int t = (nal[off] & 0xFF) & 0x1F; if (t == NAL_TYPE_IDR) flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME; }
                        decoder.queueInputBuffer(inIdx, 0, nal.length, System.nanoTime() / 1000, flags);
                    }
                }

                int outIdx = decoder.dequeueOutputBuffer(info, 5000);
                if (outIdx >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        frameCount++;
                        long now = System.currentTimeMillis();
                        if (now - lastFpsTime >= 2000) {
                            int fps = (int) (frameCount * 1000.0 / (now - lastFpsTime));
                            handler.post(() -> tvStatus.setText(fps + " FPS | " + totalPackets + " pkts"));
                            appendLog("I", "[DECODE] " + fps + " FPS frames=" + frameCount);
                            frameCount = 0; lastFpsTime = now;
                        }
                    }
                    decoder.releaseOutputBuffer(outIdx, true);
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat fmt = decoder.getOutputFormat();
                    appendLog("I", "[CODEC] " + fmt.getInteger(MediaFormat.KEY_WIDTH) + "x" + fmt.getInteger(MediaFormat.KEY_HEIGHT));
                }
            } catch (InterruptedException e) { break; }
            catch (Exception e) {
                appendLog("E", "[DECODE] " + e.getMessage());
                if (decoder != null) { try { decoder.stop(); } catch (Exception ig) {} releaseDecoder(); }
                try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
            }
        }
        appendLog("I", "[DECODE] 退出");
    }

    private void releaseDecoder() {
        decoderConfigured = false;
        if (decoder != null) { try { decoder.stop(); decoder.release(); } catch (Exception ig) {} decoder = null; }
    }

    // =========================================================================
    // Connection management
    // =========================================================================

    private void stopStreaming() {
        streaming = false;
        if (streamThread != null) { streamThread.interrupt(); streamThread = null; }
        if (tcpThread != null) { tcpThread.interrupt(); tcpThread = null; }
        if (tcpSocket != null) { try { tcpSocket.close(); } catch (Exception ig) {} tcpSocket = null; }
        if (udpSocket != null) { try { udpSocket.close(); } catch (Exception ig) {} udpSocket = null; }
        handler.post(() -> { if (progressBar != null) progressBar.setVisibility(View.GONE); });
    }

    // =========================================================================
    // Snapshot
    // =========================================================================

    private void takeSnapshot() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            return;
        }
        try {
            surfaceView.setDrawingCacheEnabled(true);
            Bitmap bitmap = surfaceView.getDrawingCache();
            if (bitmap != null) {
                android.provider.MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "M8_" + System.currentTimeMillis() + ".jpg", "M8");
                Toast.makeText(this, "照片已保存", Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this, "无画面", Toast.LENGTH_SHORT).show();
            surfaceView.setDrawingCacheEnabled(false);
        } catch (Exception e) { surfaceView.setDrawingCacheEnabled(false); Toast.makeText(this, "失败: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    // =========================================================================
    // TCP commands
    // =========================================================================

    private void sendTcpCommand(String cmd) {
        appendLog("I", "[CMD] " + cmd);
        new Thread(() -> {
            Socket s = null;
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(config.ip, config.tcpPort), 3000);
                s.setSoTimeout(3000);
                JSONObject j = new JSONObject();
                j.put("CMD", getCmdCode(cmd)); j.put("PARAM", "");
                OutputStream os = s.getOutputStream();
                os.write((j.toString() + "\n").getBytes("UTF-8")); os.flush();
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                String resp = br.readLine();
                appendLog("D", "[CMD] resp: " + resp);
                s.close();
                handler.post(() -> Toast.makeText(this, getCmdLabel(cmd) + " OK", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                appendLog("E", "[CMD] 失败: " + e.getMessage());
                if (s != null) try { s.close(); } catch (Exception ig) {}
                handler.post(() -> Toast.makeText(this, getCmdLabel(cmd) + " 失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private int getCmdCode(String c) { switch(c) { case "takeoff": return 1; case "land": return 2; case "emergency": return 3; case "speed": return 10; default: return 0; } }
    private String getCmdLabel(String c) { switch(c) { case "takeoff": return "起飞"; case "land": return "降落"; case "emergency": return "紧急停止"; case "speed": return "变速"; default: return c; } }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) takeSnapshot();
            else Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show();
        }
    }
}
