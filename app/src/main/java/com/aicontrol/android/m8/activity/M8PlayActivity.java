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
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M8/H8 Drone video stream - v0.0.94
 * 
 * Protocol flow based on H8 APK reverse engineering:
 * 1. Connect WiFi → auto TCP:4646 (JSON control)
 * 2. TCP:4646 send JSON query → get firmware/device info
 * 3. TCP:4646 send video activation command
 * 4. UDP:1563 send 3-byte handshake {0xD8, 0xC0, 0xD9}
 * 5. Receive H264 RTP data → MediaCodec decode → SurfaceView render
 * 
 * Key insight: TCP:4646 must complete authentication/activation BEFORE UDP:1563 opens
 */
public class M8PlayActivity extends com.aicontrol.android.base.BaseActivity {

    private static final String TAG = "M8PlayActivity";
    private static final int STORAGE_PERMISSION_CODE = 1001;
    private static final int MAX_BUF = 204800;

    // H264 NAL types
    private static final int NAL_SPS = 7, NAL_PPS = 8, NAL_IDR = 5, NAL_SEI = 6;
    // RTP constants
    private static final int RTP_HDR_MIN = 12, RTP_STAP_A = 24, RTP_FU_A = 28;

    // Handshake bytes from H8 APK native code
    private static final byte[] HANDSHAKE = {(byte)0xD8, (byte)0xC0, (byte)0xD9};

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

    // MediaCodec decoder
    private MediaCodec decoder;
    private boolean decoderConfigured = false;
    private byte[] spsData = null;
    private byte[] ppsData = null;
    private BlockingQueue<byte[]> nalQueue = new LinkedBlockingQueue<>(60);

    // Stats
    private int frameCount = 0;
    private int totalPackets = 0;
    private int totalBytes = 0;
    private long lastFpsTime = System.currentTimeMillis();
    private AtomicInteger logCount = new AtomicInteger(0);
    private StringBuilder logBuilder = new StringBuilder();

    // FU-A reassembly
    private int fuSeq = -1;
    private ByteBuffer fuBuffer = null;
    private boolean fuStarted = false;

    // TCP:4646 response collector
    private final java.util.List<String> tcp4646Responses = new java.util.ArrayList<>();

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
    // Log & Export
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
    // Logging
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

    private String truncate(String s, int max) { return s.length() > max ? s.substring(0, max) + "..." : s; }

    // =========================================================================
    // TCP Port Scan (quick check)
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
                    byte[] buf = new byte[512];
                    int n = s.getInputStream().read(buf, 0, buf.length);
                    if (n > 0) {
                        String banner = new String(buf, 0, Math.min(n, 200)).split("\r?\n")[0];
                        appendLog("I", "[TCP] :" + p + " OPEN banner=" + truncate(banner, 100));
                    } else {
                        appendLog("I", "[TCP] :" + p + " OPEN (无banner)");
                    }
                } catch (Exception e) { appendLog("I", "[TCP] :" + p + " OPEN (无banner)"); }
                s.close();
            } catch (Exception e) { if (s != null) try { s.close(); } catch (Exception ig) {} }
        }
        int[] r = new int[cnt];
        System.arraycopy(open, 0, r, 0, cnt);
        appendLog("I", "[SCAN] 开放: " + java.util.Arrays.toString(r));
        return r;
    }

    // =========================================================================
    // STEP 1: TCP:4646 Connect & Query (H8 native protocol)
    // =========================================================================

    /**
     * Connect to TCP:4646, exchange JSON commands.
     * This is the H8's control channel. Must complete BEFORE UDP video port opens.
     * 
     * Based on H8 APK analysis:
     * - App connects TCP:4646 immediately after WiFi association
     * - Sends JSON queries for firmware version, device info
     * - Sends video start command {"CMD":20} before UDP handshake
     */
    /**
     * v0.0.98: Connect TCP:4646 using H8 native protocol
     * 
     * Key findings:
     * - CMD:2 → server responds {CMD:2, PARAM:-1, RESULT:0} (accepted but PARAM=-1)
     * - ALL UDP ports ICMP unreachable — drone doesn't open UDP at all
     * - TCP:7070 is also open — may carry video data
     * - Need to try: CMD with individual reads + TCP:7070 video
     */
    private boolean connectAndQuery4646() {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(config.ip, config.tcpPort), 3000);
            s.setSoTimeout(3000);
            s.setTcpNoDelay(true);
            appendLog("I", "[4646] 已连接 TCP:" + config.tcpPort);

            java.io.OutputStream os = s.getOutputStream();
            java.io.InputStream is = s.getInputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            DataInputStream dis = new DataInputStream(is);

            // Step 1: Wait for server banner push (CMD:0 with firmware info)
            // Original H8: server sends {"CMD":0,"RESULT":0,"PARAM":"{\"FirmWare\":...}"}
            // immediately upon connection. Client does NOT request it.
            appendLog("I", "[4646] 等待服务器推送 banner...");
            byte[] recvBuf = new byte[1024];
            boolean bannerReceived = false;
            
            try {
                s.setSoTimeout(5000);
                int bytesRead = dis.read(recvBuf);
                if (bytesRead > 0) {
                    String data = new String(recvBuf, 0, bytesRead, "ISO-8859-1").trim();
                    
                    // Log hex
                    StringBuilder hexStr = new StringBuilder();
                    for (int i = 0; i < Math.min(bytesRead, 32); i++) {
                        hexStr.append(String.format("%02X ", recvBuf[i] & 0xFF));
                    }
                    appendLog("D", "[4646] recv " + bytesRead + "B hex: " + hexStr.toString());
                    
                    appendLog("I", "[4646] 服务器推送: " + truncate(data, 500));
                    tcp4646Responses.add(data);
                    
                    try {
                        JSONObject j = new JSONObject(data);
                        int cmd = j.optInt("CMD", -1);
                        int result = j.optInt("RESULT", -1);
                        String param = j.optString("PARAM", "");
                        appendLog("I", "[4646] CMD=" + cmd + " RESULT=" + result + " PARAM=" + truncate(param, 300));
                        
                        if (cmd == 0 && result == 0) {
                            bannerReceived = true;
                            appendLog("I", "[4646] ★ 收到固件 banner!");
                            
                            // Parse firmware info
                            try {
                                JSONObject paramJson = new JSONObject(param);
                                String firmware = paramJson.optString("FirmWare", "");
                                String platform = paramJson.optString("platform", "");
                                appendLog("I", "[4646] 固件: " + firmware + " 平台: " + platform);
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
            } catch (SocketTimeoutException e) {
                appendLog("W", "[4646] 等待banner超时 (5s)");
            }

            if (!bannerReceived) {
                appendLog("W", "[4646] 未收到banner, 尝试发送查询...");
                // Fallback: try sending CMD:0 query like old version
                try {
                    String q = "{\"CMD\":0,\"PARAM\":\"\"}";
                    writer.write(q.replace("\n", " ") + "\n");
                    writer.flush();
                    Thread.sleep(50);
                    appendLog("D", "[4646] 发送: " + q);
                    
                    s.setSoTimeout(3000);
                    int bytesRead = dis.read(recvBuf);
                    if (bytesRead > 0) {
                        String data = new String(recvBuf, 0, bytesRead, "ISO-8859-1").trim();
                        appendLog("I", "[4646] 查询响应: " + truncate(data, 500));
                        tcp4646Responses.add(data);
                        bannerReceived = true;
                    }
                } catch (Exception e) {
                    appendLog("W", "[4646] 查询也失败: " + e.getMessage());
                }
            }

            // Step 2: v0.0.98 — Send video commands ONE BY ONE with individual responses
            if (bannerReceived) {
                appendLog("I", "[4646] 发送视频激活命令序列...");
                try {
                    // Send each command and read individual response
                    String[] cmds = {
                        "{\"CMD\":94,\"PARAM\":\"1563\"}",       // BIND_APP_UDP
                        "{\"CMD\":2,\"PARAM\":\"\"}",          // VID_ENC_PREVIEW_ON (empty param)
                        "{\"CMD\":2,\"PARAM\":\"1\"}",          // VID_ENC_PREVIEW_ON (param=1)
                        "{\"CMD\":2,\"PARAM\":\"on\"}",        // VID_ENC_PREVIEW_ON (param=on)
                        "{\"CMD\":4,\"PARAM\":\"\"}",          // VID_ENC_START (start recording)
                        "{\"CMD\":116,\"PARAM\":\"\"}",        // CMD_TCP_CONNECTED
                    };
                    
                    for (String cmd : cmds) {
                        writer.write(cmd + "\n");
                        writer.flush();
                        appendLog("D", "[4646] 发送: " + cmd);
                        
                        // Wait for individual response
                        s.setSoTimeout(1500);
                        try {
                            int rb = dis.read(recvBuf);
                            if (rb > 0) {
                                StringBuilder hx = new StringBuilder();
                                for (int i = 0; i < Math.min(rb, 32); i++) {
                                    hx.append(String.format("%02X ", recvBuf[i] & 0xFF));
                                }
                                String txt = new String(recvBuf, 0, rb, "ISO-8859-1").trim();
                                appendLog("D", "[4646] " + rb + "B hex: " + hx.toString());
                                if (!txt.isEmpty()) {
                                    appendLog("I", "[4646] 响应: " + truncate(txt, 300));
                                }
                            }
                        } catch (SocketTimeoutException e) {
                            appendLog("I", "[4646] 无响应 (1500ms超时)");
                        }
                        Thread.sleep(200);
                    }
                    
                    // Also try raw binary commands (some drones use binary not JSON)
                    appendLog("I", "[4646] 尝试二进制命令...");
                    byte[][] binCmds = {
                        {(byte)0xD8, (byte)0xC0, (byte)0xD9},  // UDP handshake on TCP?
                        {0x01, 0x00, 0x00, 0x00},               // Simple ping
                        {0x02, 0x00, 0x00, 0x00},               // CMD:2 binary?
                    };
                    for (byte[] bc : binCmds) {
                        os.write(bc);
                        os.flush();
                        StringBuilder hx = new StringBuilder();
                        for (byte b : bc) hx.append(String.format("%02X ", b & 0xFF));
                        appendLog("D", "[4646] 发送二进制: " + hx.toString().trim());
                        
                        s.setSoTimeout(1000);
                        try {
                            int rb = dis.read(recvBuf);
                            if (rb > 0) {
                                StringBuilder hx2 = new StringBuilder();
                                for (int i = 0; i < Math.min(rb, 64); i++) {
                                    hx2.append(String.format("%02X ", recvBuf[i] & 0xFF));
                                }
                                appendLog("D", "[4646] 二进制响应 " + rb + "B: " + hx2.toString());
                            }
                        } catch (SocketTimeoutException ignored) {}
                        Thread.sleep(200);
                    }
                } catch (Exception e) {
                    appendLog("W", "[4646] 发送激活命令失败: " + e.getMessage());
                }
            }

            // Step 3: Keep TCP alive for background reading
            s.setSoTimeout(100);
            tcpSocket = s;
            final Socket tcpSock = s;
            tcpThread = new Thread(() -> {
                try {
                    byte[] buf2 = new byte[1024];
                    DataInputStream dis2 = new DataInputStream(tcpSock.getInputStream());
                    while (streaming) {
                        try {
                            int nr = dis2.read(buf2);
                            if (nr <= 0) break;
                            // Always log hex dump for binary analysis
                            StringBuilder hexStr = new StringBuilder();
                            for (int i = 0; i < Math.min(nr, 64); i++) {
                                hexStr.append(String.format("%02X ", buf2[i] & 0xFF));
                            }
                            String msg = new String(buf2, 0, nr, "ISO-8859-1").trim();
                            appendLog("D", "[4646] " + nr + "B hex: " + hexStr.toString());
                            if (!msg.isEmpty()) {
                                appendLog("D", "[4646] text: " + truncate(msg, 300));
                                tcp4646Responses.add(msg);
                            }
                        } catch (SocketTimeoutException e) {
                            // No data, continue
                        }
                    }
                } catch (Exception e) {
                    if (streaming) appendLog("W", "[4646] 断开: " + e.getMessage());
                }
            }, "TCP4646");
            tcpThread.start();

            return bannerReceived;

        } catch (Exception e) {
            appendLog("W", "[4646] 连接失败: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // STEP 2: UDP Video Stream (H8 protocol: handshake + RTP)
    // =========================================================================

    /**
     * Connect UDP:1563, send handshake, receive H264 RTP.
     * Must be called AFTER TCP:4646 activation.
     * 
     * H8 APK flow:
     * 1. Create UDP socket
     * 2. Send 3-byte handshake {0xD8, 0xC0, 0xD9}
     * 3. Receive H264 RTP packets
     * 4. Parse RTP → NAL units → nativeUdpData() → Live555 → H264 decode → SDL2 render
     */
    private boolean tryUdpVideoStream(int port) {
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket(null);
            ds.setReuseAddress(true);
            ds.setBroadcast(true);
            // v0.0.97: Use random local port (don't bind to remote port 1563)
            // The drone sends TO our port, we send FROM random port TO drone's port
            ds.bind(new InetSocketAddress(0));  // OS assigns random local port
            ds.setSoTimeout(5000);
            ds.connect(new InetSocketAddress(config.ip, port));
            ds.setReceiveBufferSize(102400);  // 100KB buffer for video
            udpSocket = ds;
            appendLog("I", "[UDP] :" + port + " 本地端口=" + ds.getLocalPort());

            // Send H8 native handshake
            DatagramPacket hpkt = new DatagramPacket(HANDSHAKE, HANDSHAKE.length);
            ds.send(hpkt);
            appendLog("I", "[UDP] 握手已发: " + hex(HANDSHAKE, HANDSHAKE.length));

            // Also try alternative handshakes in sequence
            // If first doesn't work within 3s, try others
            boolean gotData = false;

            // Start decoder thread
            Thread dt = new Thread(this::decoderLoop, "DecodeThread");
            dt.start();

            handler.post(() -> tvStatus.setText("UDP:" + port + " 等待视频..."));

            byte[] buf = new byte[MAX_BUF];
            int timeouts = 0;
            int handshakeRetries = 0;

            while (streaming) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    ds.receive(pkt);
                    int len = pkt.getLength();
                    totalPackets++;
                    totalBytes += len;
                    timeouts = 0;

                    if (!dataReceived) {
                        dataReceived = true;
                        gotData = true;
                        handler.post(() -> progressBar.setVisibility(View.GONE));
                        appendLog("I", ">>> UDP首包! port=" + port + " len=" + len);
                        appendLog("I", ">>> 首包hex: " + hex(buf, Math.min(len, 64)));
                    }

                    if (totalPackets <= 30) {
                        appendLog("D", "[UDP] #" + totalPackets + " len=" + len + " [" + hex(buf, Math.min(len, 32)) + "]");
                    }

                    // Parse the received data
                    parseVideoData(buf, len);

                    if (totalPackets % 100 == 0) {
                        long now = System.currentTimeMillis();
                        double rate = totalBytes * 1000.0 / Math.max(1, now - lastFpsTime);
                        handler.post(() -> tvStatus.setText("UDP:" + port + " " + String.format("%.0f", rate / 1024) + " KB/s"));
                        appendLog("I", "[STATS] " + totalPackets + " pkts " + String.format("%.0f", rate) + " B/s");
                    }

                } catch (SocketTimeoutException e) {
                    timeouts++;
                    if (!dataReceived && timeouts >= 2 && handshakeRetries < 5) {
                        // Try alternative handshake patterns
                        handshakeRetries++;
                        appendLog("I", "[UDP] 重试握手 #" + handshakeRetries);

                        byte[][] altHandshakes = {
                            {0x00, (byte)0xD8, (byte)0xC0, (byte)0xD9},  // 4-byte variant
                            {(byte)0xAA, (byte)0x55, 0x00, 0x01},        // Alternative pattern
                            {0x01, 0x00, 0x00, 0x00},                     // Simple ping
                            HANDSHAKE,                                     // Retry original
                            "LIVE555".getBytes(),                           // Live555 marker
                        };

                        byte[] hs = altHandshakes[(handshakeRetries - 1) % altHandshakes.length];
                        try {
                            ds.send(new DatagramPacket(hs, hs.length));
                            appendLog("I", "[UDP] 发送: " + hex(hs, hs.length));
                        } catch (Exception ignored) {}

                        // Also try resending activation on TCP
                        if (handshakeRetries == 2) {
                            resendTcpActivation();
                        }

                        timeouts = 0;
                        continue;
                    }

                    if (!dataReceived && timeouts >= 4) {
                        appendLog("W", "[UDP] :" + port + " 无数据, 切换策略");
                        return false;
                    }
                    if (dataReceived && timeouts >= 10) {
                        appendLog("W", "[UDP] 数据中断");
                        break;
                    }
                } catch (PortUnreachableException e) {
                    appendLog("W", "[UDP] :" + port + " ICMP不可达 - 端口未开放!");
                    // Try sending activation again
                    if (handshakeRetries < 2) {
                        appendLog("I", "[UDP] ICMP不可达, 尝试TCP激活后重试...");
                        resendTcpActivation();
                        Thread.sleep(2000);
                        // Retry handshake
                        try {
                            ds.send(new DatagramPacket(HANDSHAKE, HANDSHAKE.length));
                            appendLog("I", "[UDP] 重新握手");
                            handshakeRetries = 99; // Skip normal retry
                            timeouts = 0;
                            continue;
                        } catch (Exception ignored) {}
                    }
                    return false;
                }
            }
            return dataReceived;
        } catch (Exception e) {
            appendLog("W", "[UDP] :" + port + " " + e.getMessage());
            return false;
        } finally {
            if (ds != null && ds != udpSocket) try { ds.close(); } catch (Exception ig) {}
        }
    }

    private void resendTcpActivation() {
        try {
            if (tcpSocket != null && tcpSocket.isConnected() && !tcpSocket.isClosed()) {
                // v0.0.97: Use correct video activation commands
                // CMD:2 = VID_ENC_PREVIEW_ON, CMD:94 = BIND_APP_UDP
                String[] cmds = {
                    "{\"CMD\":94,\"PARAM\":\"1563\"}",
                    "{\"CMD\":2,\"PARAM\":\"\"}",
                    "{\"CMD\":2}",
                };
                for (String cmd : cmds) {
                    try {
                        OutputStream os = tcpSocket.getOutputStream();
                        os.write((cmd + "\n").getBytes("UTF-8"));
                        os.flush();
                        appendLog("I", "[4646-RETRY] " + cmd);
                    } catch (Exception e) {
                        appendLog("D", "[4646-RETRY] 失败: " + e.getMessage());
                        break;
                    }
                    Thread.sleep(200);
                }
            }
        } catch (Exception e) {
            appendLog("D", "[4646-RETRY] 错误: " + e.getMessage());
        }
    }

    // =========================================================================
    // STEP 3 (Fallback): TCP Video Stream
    // =========================================================================

    private boolean tryTcpVideoStream(int port) {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(config.ip, port), 3000);
            s.setSoTimeout(5000);
            s.setReceiveBufferSize(MAX_BUF);
            appendLog("I", "[TCP-VIDEO] :" + port + " 已连接");

            // Send handshake
            OutputStream os = s.getOutputStream();
            os.write(HANDSHAKE);
            os.flush();
            appendLog("I", "[TCP-VIDEO] 握手已发");

            Thread dt = new Thread(this::decoderLoop, "DecodeThread");
            dt.start();

            handler.post(() -> tvStatus.setText("TCP:" + port + " 等待视频..."));

            byte[] buf = new byte[MAX_BUF];
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
                        appendLog("I", ">>> 首包hex: " + hex(buf, Math.min(n, 64)));
                    }

                    if (totalPackets <= 20) {
                        appendLog("D", "[TCP-VIDEO] #" + totalPackets + " len=" + n + " [" + hex(buf, Math.min(n, 32)) + "]");
                    }

                    parseVideoData(buf, n);

                    if (totalPackets % 100 == 0) {
                        long now = System.currentTimeMillis();
                        double rate = totalBytes * 1000.0 / Math.max(1, now - lastFpsTime);
                        handler.post(() -> tvStatus.setText("TCP:" + port + " " + String.format("%.0f", rate / 1024) + " KB/s"));
                        appendLog("I", "[STATS] " + totalPackets + " pkts " + String.format("%.0f", rate) + " B/s");
                    }

                } catch (SocketTimeoutException e) {
                    timeouts++;
                    if (!dataReceived && timeouts >= 3) {
                        appendLog("I", "[TCP-VIDEO] 无数据, 切换");
                        break;
                    }
                    try { os.write(HANDSHAKE); os.flush(); } catch (Exception ig) {}
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
    // Video Data Parser (handles H264 raw, RTP, MJPEG, HTTP)
    // =========================================================================

    private void parseVideoData(byte[] buf, int len) {
        // 1. Check for H264 start codes (00 00 00 01 or 00 00 01)
        int nalStart = findH264Start(buf, len);
        if (nalStart >= 0) {
            if (totalPackets <= 10) appendLog("D", "[DATA] H264 start code at offset " + nalStart);
            parseH264Stream(buf, len);
            return;
        }

        // 2. Check for MJPEG (FF D8)
        for (int i = 0; i < Math.min(len - 1, 100); i++) {
            if ((buf[i] & 0xFF) == 0xFF && (buf[i+1] & 0xFF) == 0xD8) {
                appendLog("I", "[DATA] JPEG at offset " + i + " - MJPEG流!");
                return;
            }
        }

        // 3. Check if RTP packet (version=2, top 2 bits = 10)
        if (len >= RTP_HDR_MIN && ((buf[0] >> 6) & 0x03) == 2) {
            if (totalPackets <= 5) appendLog("D", "[DATA] RTP packet detected");
            parseRtpPacket(buf, len);
            return;
        }

        // 4. Check if HTTP response
        if (len >= 4 && buf[0] == 'H' && buf[1] == 'T' && buf[2] == 'T' && buf[3] == 'P') {
            if (totalPackets <= 5) {
                String header = new String(buf, 0, Math.min(len, 200));
                String firstLine = header.split("\r?\n")[0];
                appendLog("D", "[DATA] HTTP: " + firstLine);
            }
            return;
        }

        if (totalPackets <= 10) {
            appendLog("D", "[DATA] 未知格式 first=" + hex(buf, Math.min(len, 32)));
        }
    }

    // =========================================================================
    // Main Connection Flow
    // =========================================================================

    private void startStreaming() {
        if (streaming) return;
        streaming = true;
        dataReceived = false;
        nalQueue.clear();
        fuStarted = false; fuBuffer = null; fuSeq = -1;
        spsData = null; ppsData = null;
        frameCount = 0; totalPackets = 0; totalBytes = 0;
        tcp4646Responses.clear();

        progressBar.setVisibility(View.VISIBLE);
        lyNotConnected.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("连接中...");
        if (btnConnect != null) btnConnect.setVisibility(View.GONE);

        appendLog("I", "========== v0.0.98 H8协议模式 ==========");
        logNetworkDiagnostics();

        streamThread = new Thread(() -> {
            try {
                // Phase 1: Connect TCP:4646 (direct, no port scan)
                // v0.0.98: connect → banner → try many CMD sequences + binary
                appendLog("I", "[PHASE1] 直接连接TCP:4646...");
                boolean tcp4646Ok = connectAndQuery4646();

                if (!tcp4646Ok) {
                    appendLog("W", "[PHASE1] TCP连接/banner失败, 仍然尝试...");
                }

                // v0.0.98: Try TCP:7070 FIRST (before UDP, since UDP ports are all closed)
                appendLog("I", "[PHASE2] TCP:7070 视频流探测...");
                boolean videoOk = tryTcpVideoStream(7070);

                if (videoOk) {
                    appendLog("I", ">>> TCP:7070 视频连接成功!");
                }

                // Phase 3: Try UDP:1563 (only once, not exhaustive)
                if (!videoOk) {
                    Thread.sleep(500);
                    appendLog("I", "[PHASE3] UDP:" + config.udpPort + " H264/RTP视频流");
                    videoOk = tryUdpVideoStream(config.udpPort);
                }

                if (!videoOk && streaming) {
                    appendLog("E", "===== 未建立视频连接 =====");
                    appendLog("I", "排查建议:");
                    appendLog("I", "1. 确认无人机已开机并连上WiFi");
                    appendLog("I", "2. 确认原厂HFun App可以正常看视频");
                    appendLog("I", "3. 在无人机热点下用tcpdump抓包验证端口");
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
    // H264 Stream Parser
    // =========================================================================

    private int findH264Start(byte[] buf, int len) {
        for (int i = 0; i < len - 3; i++) {
            if (buf[i] == 0 && buf[i+1] == 0 && (buf[i+2] == 1 || (buf[i+2] == 0 && i + 3 < len && buf[i+3] == 1))) {
                return i;
            }
        }
        return -1;
    }

    private void parseH264Stream(byte[] buf, int len) {
        int i = 0;
        while (i < len - 3) {
            int start = -1;
            if (buf[i] == 0 && buf[i+1] == 0 && buf[i+2] == 0 && buf[i+3] == 1) start = i + 4;
            else if (buf[i] == 0 && buf[i+1] == 0 && buf[i+2] == 1) start = i + 3;

            if (start >= 0 && start < len) {
                int end = len;
                for (int j = start + 1; j < len - 3; j++) {
                    if (buf[j] == 0 && buf[j+1] == 0 && (buf[j+2] == 1 || (buf[j+2] == 0 && buf[j+3] == 1))) {
                        end = j; break;
                    }
                }
                byte[] nal = new byte[end - start];
                System.arraycopy(buf, start, nal, 0, nal.length);
                if (nal.length > 0) handleNalUnit(nal);
                i = start + 1;
            } else {
                i++;
            }
        }
    }

    // =========================================================================
    // RTP Parser
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

        int headerSize = RTP_HDR_MIN + (csrcCount * 4);
        if (extension == 1 && length > headerSize + 4) {
            int extLen = ((data[headerSize+2] & 0xFF) << 8) | (data[headerSize+3] & 0xFF);
            headerSize += 4 + (extLen * 4);
        }
        if (headerSize >= length) return;
        int payloadOffset = headerSize;
        int payloadLength = length - headerSize;
        if (padding == 1 && payloadLength > 0) payloadLength -= (data[length-1] & 0xFF);
        if (payloadLength <= 0) return;

        if (totalPackets <= 5) {
            appendLog("D", String.format("RTP: PT=%d Seq=%d M=%d Pay=%d", payloadType, seqNum, marker, payloadLength));
        }

        int nalHeaderByte = data[payloadOffset] & 0xFF;
        int nalType = nalHeaderByte & 0x1F;

        if (nalType >= 1 && nalType <= 23) {
            // Single NAL unit
            byte[] nal = new byte[payloadLength + 4];
            nal[0] = 0; nal[1] = 0; nal[2] = 0; nal[3] = 1;
            System.arraycopy(data, payloadOffset, nal, 4, payloadLength);
            handleNalUnit(nal);
        } else if (nalType == RTP_STAP_A) {
            parseStapA(data, payloadOffset, payloadLength);
        } else if (nalType == RTP_FU_A) {
            parseFuA(data, payloadOffset, payloadLength, seqNum, marker);
        }
    }

    private void parseStapA(byte[] data, int offset, int length) {
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
    // NAL Handling + MediaCodec
    // =========================================================================

    private void handleNalUnit(byte[] nal) {
        if (nal.length < 5) return;
        int dataOff = 0;
        if (nal.length >= 4 && nal[0] == 0 && nal[1] == 0 && nal[2] == 0 && nal[3] == 1) dataOff = 4;
        else if (nal.length >= 3 && nal[0] == 0 && nal[1] == 0 && nal[2] == 1) dataOff = 3;

        if (nal.length - dataOff < 1) return;
        int nalType = (nal[dataOff] & 0xFF) & 0x1F;

        if (nalType == NAL_SPS) {
            spsData = new byte[nal.length - dataOff];
            System.arraycopy(nal, dataOff, spsData, 0, spsData.length);
            appendLog("I", ">>> SPS! size=" + spsData.length + " [" + hex(spsData, Math.min(spsData.length, 32)) + "]");
            tryConfigureDecoder();
            return;
        }
        if (nalType == NAL_PPS) {
            ppsData = new byte[nal.length - dataOff];
            System.arraycopy(nal, dataOff, ppsData, 0, ppsData.length);
            appendLog("I", ">>> PPS! size=" + ppsData.length + " [" + hex(ppsData, Math.min(ppsData.length, 16)) + "]");
            tryConfigureDecoder();
            return;
        }
        if (nalType == NAL_SEI) return;

        if (decoderConfigured) {
            if (nalQueue.remainingCapacity() == 0) nalQueue.poll();
            nalQueue.offer(nal);
        }
    }

    private void tryConfigureDecoder() {
        if (decoderConfigured || spsData == null || ppsData == null) return;
        handler.post(() -> {
            if (decoderConfigured) return;
            int[][] res = {{1280,720},{960,720},{856,480},{640,480},{1920,1080},{854,480},{640,360}};
            for (int[] r : res) {
                try {
                    MediaFormat fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, r[0], r[1]);
                    fmt.setByteBuffer("csd-0", ByteBuffer.wrap(spsData));
                    fmt.setByteBuffer("csd-1", ByteBuffer.wrap(ppsData));
                    fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_BUF);
                    decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                    decoder.configure(fmt, surfaceHolder.getSurface(), null, 0);
                    decoder.start();
                    decoderConfigured = true;
                    appendLog("I", ">>> MediaCodec启动! " + r[0] + "x" + r[1]);
                    return;
                } catch (Exception e) {
                    if (decoder != null) { try { decoder.release(); } catch (Exception ig) {} decoder = null; }
                    appendLog("D", "[CODEC] " + r[0] + "x" + r[1] + " 失败: " + e.getMessage());
                }
            }
            appendLog("W", "[CODEC] 所有分辨率均失败");
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
                        int off = (nal.length >= 4 && nal[0]==0 && nal[1]==0 && nal[2]==0 && nal[3]==1) ? 4 :
                                  (nal.length >= 3 && nal[0]==0 && nal[1]==0 && nal[2]==1) ? 3 : 0;
                        if (off < nal.length) { int t = (nal[off] & 0xFF) & 0x1F; if (t == NAL_IDR) flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME; }
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
                    appendLog("I", "[CODEC] 输出: " + fmt.getInteger(MediaFormat.KEY_WIDTH) + "x" + fmt.getInteger(MediaFormat.KEY_HEIGHT));
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
    // Connection Management
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
    // TCP Commands
    // =========================================================================

    private void sendTcpCommand(String cmd) {
        appendLog("I", "[CMD] " + cmd);
        new Thread(() -> {
            // Try existing connection first
            if (tcpSocket != null && tcpSocket.isConnected() && !tcpSocket.isClosed()) {
                try {
                    OutputStream os = tcpSocket.getOutputStream();
                    JSONObject j = new JSONObject();
                    j.put("CMD", getCmdCode(cmd)); j.put("PARAM", "");
                    os.write((j.toString() + "\n").getBytes("UTF-8"));
                    os.flush();
                    appendLog("I", "[CMD] 已通过现有4646连接发送");
                    handler.post(() -> Toast.makeText(this, getCmdLabel(cmd) + " 已发送", Toast.LENGTH_SHORT).show());
                    return;
                } catch (Exception e) {
                    appendLog("D", "[CMD] 现有连接发送失败, 新建连接");
                }
            }

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
