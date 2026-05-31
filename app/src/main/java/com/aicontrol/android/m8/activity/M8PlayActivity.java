package com.aicontrol.android.m8.activity;

import android.Manifest;
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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
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
 * M8/H8 Drone video stream playback activity with debug log panel.
 * v0.0.90 - Port scan + RTSP + TCP activation + graceful ICMP handling
 */
public class M8PlayActivity extends com.aicontrol.android.base.BaseActivity {

    private static final String TAG = "M8PlayActivity";
    private static final int STORAGE_PERMISSION_CODE = 1001;
    private static final int MAX_UDP_PACKET_SIZE = 102400;
    private static final int RTP_HEADER_MIN_SIZE = 12;

    private static final int NAL_TYPE_SPS = 7;
    private static final int NAL_TYPE_PPS = 8;
    private static final int NAL_TYPE_IDR = 5;
    private static final int NAL_TYPE_SEI = 6;
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
        if (!streaming) {
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
            @Override public void surfaceCreated(SurfaceHolder holder) {
                appendLog("I", "Surface created");
            }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
                appendLog("I", "Surface changed: " + w + "x" + h);
            }
            @Override public void surfaceDestroyed(SurfaceHolder holder) {
                appendLog("I", "Surface destroyed");
            }
        });

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        ImageButton btnLog = findViewById(R.id.btnLog);
        btnLog.setOnClickListener(v -> {
            int vis = lyLogPanel.getVisibility();
            lyLogPanel.setVisibility(vis == View.GONE ? View.VISIBLE : View.GONE);
        });

        ImageButton btnConfig = findViewById(R.id.btnConfig);
        btnConfig.setOnClickListener(v -> {
            stopStreaming();
            startActivity(new Intent(this, M8ConfigActivity.class));
        });

        TextView btnClearLog = findViewById(R.id.btnClearLog);
        btnClearLog.setOnClickListener(v -> {
            logBuilder.setLength(0);
            logCount.set(0);
            tvLogContent.setText("");
            tvLogCount.setText("0");
        });

        com.aicontrol.android.widget.KButton btnGoConfig = findViewById(R.id.btnGoConfig);
        btnGoConfig.setOnClickListener(v -> startActivity(new Intent(this, M8ConfigActivity.class)));

        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> {
                stopStreaming();
                handler.postDelayed(() -> startStreaming(), 500);
            });
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
    // Network Diagnostics
    // =========================================================================

    private void logNetworkDiagnostics() {
        appendLog("I", "===== 网络诊断开始 =====");
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String ssid = wifiInfo.getSSID();
            int rssi = wifiInfo.getRssi();
            int linkSpeed = wifiInfo.getLinkSpeed();
            int ipInt = wifiInfo.getIpAddress();
            String localIp = intToIp(ipInt);
            appendLog("I", "[WiFi] SSID=" + ssid + " RSSI=" + rssi + "dBm Speed=" + linkSpeed + "Mbps");
            appendLog("I", "[WiFi] 本机IP=" + localIp);
        } catch (Exception e) {
            appendLog("W", "[WiFi] 获取失败: " + e.getMessage());
        }

        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dhcp = wifiManager.getDhcpInfo();
            appendLog("I", "[DHCP] Gateway=" + intToIp(dhcp.gateway) + " Mask=" + intToIp(dhcp.netmask) + " DNS=" + intToIp(dhcp.dns1));
        } catch (Exception e) {
            appendLog("W", "[DHCP] 失败: " + e.getMessage());
        }

        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress a = addrs.nextElement();
                        if (a instanceof Inet4Address) appendLog("I", "[IF] " + ni.getName() + " " + a.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {}
        appendLog("I", "===== 网络诊断完成 =====");
    }

    private static String intToIp(int addr) {
        return ((addr & 0xFF) + "." + ((addr >> 8) & 0xFF) + "." + ((addr >> 16) & 0xFF) + "." + ((addr >> 24) & 0xFF));
    }

    // =========================================================================
    // Debug Log
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

    // =========================================================================
    // Port scanning - find open UDP/TCP ports
    // =========================================================================

    private int[] scanUdpPorts() {
        // Common video streaming ports
        int[] ports = {1563, 8554, 8553, 554, 5000, 5001, 5004, 5005, 5600, 7000, 7070, 8000, 8080, 9000, 9100, 10000, 1234, 3000, 3001, 1935, 4444, 6666, 7777, 8888, 9999};
        int[] openPorts = new int[ports.length];
        int openCount = 0;

        appendLog("I", "[UDP-SCAN] 扫描 " + ports.length + " 个UDP端口...");

        for (int port : ports) {
            DatagramSocket ds = null;
            try {
                ds = new DatagramSocket();
                ds.setSoTimeout(1500); // 1.5s per port
                ds.connect(new InetSocketAddress(config.ip, port));

                // Send handshake probe
                byte[] probe = new byte[]{(byte) 0xD8, (byte) 0xC0, (byte) 0xD9};
                DatagramPacket pkt = new DatagramPacket(probe, probe.length);
                ds.send(pkt);

                // Try to receive (if port is open, we should NOT get PortUnreachable)
                byte[] buf = new byte[4096];
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                try {
                    ds.receive(recv);
                    // Got data back - port is OPEN!
                    openPorts[openCount++] = port;
                    appendLog("I", "[UDP-SCAN] >>> OPEN: :" + port + " (收到数据 len=" + recv.getLength() + " hex=" + bytesToHexFull(buf, Math.min(recv.getLength(), 32)) + ")");
                } catch (SocketTimeoutException ste) {
                    // Timeout - port might be open (firewall drops) or filtered
                    appendLog("D", "[UDP-SCAN] :" + port + " 超时(可能开放但无响应)");
                } catch (PortUnreachableException pre) {
                    // ICMP Port Unreachable - port is definitely CLOSED
                    appendLog("D", "[UDP-SCAN] :" + port + " 关闭(ICMP不可达)");
                } catch (Exception e) {
                    appendLog("D", "[UDP-SCAN] :" + port + " 异常: " + e.getClass().getSimpleName());
                }
            } catch (Exception e) {
                appendLog("D", "[UDP-SCAN] :" + port + " 发送失败: " + e.getMessage());
            } finally {
                if (ds != null) try { ds.close(); } catch (Exception ignored) {}
            }
        }

        int[] result = new int[openCount];
        System.arraycopy(openPorts, 0, result, 0, openCount);
        appendLog("I", "[UDP-SCAN] 完成! 发现 " + openCount + " 个开放UDP端口: " + java.util.Arrays.toString(result));
        return result;
    }

    private int[] scanTcpPorts() {
        int[] ports = {4646, 1563, 8554, 8553, 554, 80, 8080, 443, 5000, 5001, 8866, 9100, 3000, 3001, 1935, 4444, 6666, 7777, 8888, 9999, 1234, 7000, 7070, 9000, 10000};
        int[] openPorts = new int[ports.length];
        int openCount = 0;

        appendLog("I", "[TCP-SCAN] 扫描 " + ports.length + " 个TCP端口...");
        for (int port : ports) {
            Socket s = null;
            try {
                s = new Socket();
                s.connect(new InetSocketAddress(config.ip, port), 800);
                openPorts[openCount++] = port;
                // Try to read banner
                s.setSoTimeout(500);
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                try {
                    String banner = br.readLine();
                    appendLog("I", "[TCP-SCAN] >>> OPEN: :" + port + " banner=" + (banner != null ? banner : "null"));
                } catch (Exception e) {
                    appendLog("I", "[TCP-SCAN] >>> OPEN: :" + port + " (无banner)");
                }
                s.close();
            } catch (Exception e) {
                // closed
            }
        }
        int[] result = new int[openCount];
        System.arraycopy(openPorts, 0, result, 0, openCount);
        appendLog("I", "[TCP-SCAN] 完成! 发现 " + openCount + " 个开放TCP端口: " + java.util.Arrays.toString(result));
        return result;
    }

    // =========================================================================
    // RTSP connection attempt
    // =========================================================================

    private void tryRtsp(String url) {
        appendLog("I", "[RTSP] 尝试连接: " + url);
        try {
            // Parse RTSP URL
            String host = config.ip;
            int port = 554;
            String path = url;
            if (url.contains("://")) {
                String rest = url.split("://", 2)[1];
                int slashIdx = rest.indexOf('/');
                if (slashIdx > 0) {
                    host = rest.substring(0, slashIdx);
                    path = rest.substring(slashIdx);
                    if (host.contains(":")) {
                        String[] hp = host.split(":");
                        host = hp[0];
                        port = Integer.parseInt(hp[1]);
                    }
                }
            }

            Socket rtspSocket = new Socket();
            rtspSocket.connect(new InetSocketAddress(host, port), 3000);
            rtspSocket.setSoTimeout(3000);

            appendLog("I", "[RTSP] TCP已连接 " + host + ":" + port);

            // Send DESCRIBE
            OutputStream os = rtspSocket.getOutputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(rtspSocket.getInputStream()));

            String describe = "DESCRIBE rtsp://" + host + ":" + port + path + " RTSP/1.0\r\n"
                    + "CSeq: 1\r\n"
                    + "User-Agent: AiControl\r\n\r\n";
            os.write(describe.getBytes("UTF-8"));
            os.flush();
            appendLog("I", "[RTSP] DESCRIBE已发");

            String response = br.readLine();
            if (response != null) {
                appendLog("I", "[RTSP] 响应: " + response);
                // Read rest of headers
                while (true) {
                    String line = br.readLine();
                    if (line == null || line.isEmpty()) break;
                    appendLog("I", "[RTSP]   " + line);
                    // Check for SDP content
                    if (line.contains("Content-Length")) {
                        // Read SDP body
                        StringBuilder sdp = new StringBuilder();
                        char[] cbuf = new char[4096];
                        int n = br.read(cbuf, 0, Math.min(4096, cbuf.length));
                        if (n > 0) {
                            sdp.append(cbuf, 0, n);
                            appendLog("I", "[RTSP] SDP: " + sdp.toString());
                            // Parse SDP for video info
                            parseSdp(sdp.toString());
                        }
                    }
                }

                // Try SETUP
                String setup = "SETUP rtsp://" + host + ":" + port + path + "/track1 RTSP/1.0\r\n"
                        + "CSeq: 2\r\n"
                        + "Transport: RTP/AVP;unicast;client_port=50000-50001\r\n\r\n";
                os.write(setup.getBytes("UTF-8"));
                os.flush();
                appendLog("I", "[RTSP] SETUP已发");

                response = br.readLine();
                if (response != null) {
                    appendLog("I", "[RTSP] SETUP响应: " + response);
                    while (true) {
                        String line = br.readLine();
                        if (line == null || line.isEmpty()) break;
                        appendLog("I", "[RTSP]   " + line);
                        if (line.toLowerCase().contains("server_port")) {
                            appendLog("I", "[RTSP] >>> 服务器分配了RTP端口!");
                        }
                    }
                }
            } else {
                appendLog("W", "[RTSP] 无响应");
            }
            rtspSocket.close();
        } catch (Exception e) {
            appendLog("W", "[RTSP] 失败: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void parseSdp(String sdp) {
        appendLog("I", "[SDP] 解析视频信息...");
        String[] lines = sdp.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("m=video")) {
                appendLog("I", "[SDP] " + line);
            } else if (line.startsWith("a=rtpmap:")) {
                appendLog("I", "[SDP] " + line);
            } else if (line.startsWith("a=fmtp:")) {
                appendLog("I", "[SDP] " + line);
            } else if (line.startsWith("a=control:")) {
                appendLog("I", "[SDP] " + line);
            }
        }
    }

    // =========================================================================
    // TCP video activation - try various commands to start video
    // =========================================================================

    private void tryTcpVideoActivation(Socket existingTcp) {
        appendLog("I", "[ACTIVATE] 尝试TCP激活视频流...");

        // Common drone video activation commands
        String[] commands = {
            // JSON commands
            "{\"CMD\":20,\"PARAM\":\"\"}",    // video start
            "{\"CMD\":21,\"PARAM\":\"\"}",    // video stop
            "{\"CMD\":30,\"PARAM\":\"\"}",    // stream
            "{\"CMD\":31,\"PARAM\":\"\"}",    // stream start
            "{\"CMD\":100,\"PARAM\":\"\"}",   // query video
            "{\"name\":\"start_video\"}",
            "{\"action\":\"video_start\"}",
            "{\"msg_type\":\"video\",\"cmd\":\"start\"}",
            "{\"T\":\"live\",\"CMD\":\"VSTART\"}",
            // Plain text
            "start_video\n",
            "video_start\n",
            "START_VIDEO\n",
            "live\n",
        };

        Socket sock = existingTcp;
        boolean reuse = (sock != null && sock.isConnected() && !sock.isClosed());

        for (int i = 0; i < commands.length; i++) {
            Socket s = sock;
            boolean closeAfter = false;
            try {
                if (!reuse || !s.isConnected()) {
                    s = new Socket();
                    s.connect(new InetSocketAddress(config.ip, config.tcpPort), 2000);
                    s.setSoTimeout(2000);
                    closeAfter = true;
                }

                OutputStream os = s.getOutputStream();
                String cmd = commands[i];
                os.write(cmd.getBytes("UTF-8"));
                if (!cmd.endsWith("\n")) os.write("\n".getBytes("UTF-8"));
                os.flush();

                appendLog("D", "[ACTIVATE] 发送#" + (i + 1) + ": " + cmd.trim());

                // Try to read response
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                try {
                    String resp = br.readLine();
                    if (resp != null) {
                        appendLog("I", "[ACTIVATE] 响应#" + (i + 1) + ": " + resp);
                    }
                } catch (SocketTimeoutException ste) {
                    appendLog("D", "[ACTIVATE] #" + (i + 1) + " 无响应(超时)");
                }

                if (closeAfter) s.close();
            } catch (Exception e) {
                appendLog("D", "[ACTIVATE] #" + (i + 1) + " 失败: " + e.getClass().getSimpleName());
                if (s != sock) try { s.close(); } catch (Exception ignored) {}
            }
        }

        // Also try on different TCP ports
        int[] tcpPorts = {80, 8080, 8554, 554, 3000, 3001, 9090};
        for (int port : tcpPorts) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(config.ip, port), 1000);
                s.setSoTimeout(1000);
                appendLog("I", "[ACTIVATE] TCP:" + port + " 开放, 尝试HTTP GET...");
                OutputStream os = s.getOutputStream();
                String httpReq = "GET /live HTTP/1.1\r\nHost: " + config.ip + ":" + port + "\r\nConnection: close\r\n\r\n";
                os.write(httpReq.getBytes("UTF-8"));
                os.flush();
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                String line = br.readLine();
                if (line != null) {
                    appendLog("I", "[ACTIVATE] HTTP:" + port + " 响应: " + line);
                    // Read a few more lines
                    for (int j = 0; j < 10; j++) {
                        String hl = br.readLine();
                        if (hl == null) break;
                        appendLog("I", "[ACTIVATE]   " + hl);
                        if (hl.isEmpty()) break; // end of headers
                    }
                }
                s.close();
            } catch (Exception e) {
                // port not open
            }
        }

        appendLog("I", "[ACTIVATE] 激活尝试完成, 等2秒让无人机处理...");
    }

    // =========================================================================
    // Video stream connection
    // =========================================================================

    private void startStreaming() {
        if (streaming) return;

        streaming = true;
        dataReceived = false;
        nalQueue.clear();
        fuStarted = false;
        fuBuffer = null;
        fuSeq = -1;
        spsData = null;
        ppsData = null;
        frameCount = 0;
        totalPackets = 0;
        totalBytes = 0;

        progressBar.setVisibility(View.VISIBLE);
        lyNotConnected.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("正在连接...");
        if (btnConnect != null) btnConnect.setVisibility(View.GONE);

        appendLog("I", "========== 开始连接 (v0.0.90-scan) ==========");
        appendLog("I", "配置: IP=" + config.ip + " UDP=" + config.udpPort + " TCP=" + config.tcpPort + " FTP=" + config.ftpPort);

        logNetworkDiagnostics();

        streamThread = new Thread(() -> {
            try {
                // ========== PHASE 1: TCP Connect ==========
                appendLog("I", "[PHASE1] TCP连接 " + config.ip + ":" + config.tcpPort + " ...");
                connectTcp();

                // ========== PHASE 2: TCP Port Scan ==========
                appendLog("I", "[PHASE2] TCP端口扫描...");
                int[] tcpOpen = scanTcpPorts();

                // ========== PHASE 3: UDP Port Scan ==========
                appendLog("I", "[PHASE3] UDP端口扫描...");
                int[] udpOpen = scanUdpPorts();

                // ========== PHASE 4: RTSP Attempts ==========
                appendLog("I", "[PHASE4] RTSP尝试...");
                tryRtsp("rtsp://" + config.ip + ":554/live");
                tryRtsp("rtsp://" + config.ip + ":8554/live");
                tryRtsp("rtsp://" + config.ip + ":554/live/ch00_0.264");
                tryRtsp("rtsp://" + config.ip + ":8554/live/ch00_0.264");

                // ========== PHASE 5: TCP Video Activation ==========
                appendLog("I", "[PHASE5] TCP激活视频...");
                tryTcpVideoActivation(tcpSocket);

                // ========== PHASE 6: Re-scan UDP after activation ==========
                appendLog("I", "[PHASE6] 激活后重新扫描UDP...");
                int[] udpOpen2 = scanUdpPorts();

                // ========== PHASE 7: Try to receive on open/found ports ==========
                appendLog("I", "[PHASE7] 尝试接收数据...");

                // Collect all ports to try
                java.util.Set<Integer> portsToTry = new java.util.LinkedHashSet<>();
                // Add configured port
                portsToTry.add(config.udpPort);
                // Add open UDP ports from scans
                for (int p : udpOpen) portsToTry.add(p);
                for (int p : udpOpen2) portsToTry.add(p);
                // Add common ports that timed out (might be open with firewall)
                portsToTry.add(8554);
                portsToTry.add(554);
                portsToTry.add(5004);
                portsToTry.add(5005);

                boolean gotData = false;

                for (int port : portsToTry) {
                    if (!streaming) break;
                    if (gotData) break;

                    appendLog("I", "[RECV] 尝试UDP:" + port + " ...");
                    gotData = tryReceiveOnPort(port);
                }

                if (!gotData && streaming) {
                    appendLog("E", "所有端口均未收到视频数据");
                    appendLog("E", "建议: 1)用tcpdump抓包看原厂app用了什么端口 2)检查无人机是否需要配对/绑定");
                    handler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        lyNotConnected.setVisibility(View.VISIBLE);
                        tvStatus.setText("未收到视频数据");
                        if (btnConnect != null) btnConnect.setVisibility(View.VISIBLE);
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Stream error", e);
                appendLog("E", "异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                closeUdp();
                streaming = false;
                appendLog("I", "========== 连接结束 (包=" + totalPackets + " 字节=" + totalBytes + ") ==========");
            }
        }, "StreamThread");
        streamThread.start();
    }

    private boolean tryReceiveOnPort(int port) {
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket(null);
            ds.setReuseAddress(true);
            ds.bind(new InetSocketAddress(0));
            ds.setSoTimeout(2000);
            ds.setReceiveBufferSize(MAX_UDP_PACKET_SIZE * 10);
            ds.connect(new InetSocketAddress(config.ip, port));

            int localPort = ds.getLocalPort();
            appendLog("I", "[RECV] UDP:" + port + " 本地端口=" + localPort);

            // Send handshake
            byte[] handshake = new byte[]{(byte) 0xD8, (byte) 0xC0, (byte) 0xD9};
            sendHandshakeOnSocket(ds, handshake, "UDP:" + port + " {D8 C0 D9}");

            // Also try empty packet
            try {
                DatagramPacket empty = new DatagramPacket(new byte[0], 0);
                ds.send(empty);
                appendLog("D", "[RECV] UDP:" + port + " 空包已发");
            } catch (Exception e) {}

            handler.post(() -> tvStatus.setText("监听UDP:" + port + " ..."));

            // Start decoder
            if (!decoderConfigured) {
                Thread decodeThread = new Thread(this::decoderLoop, "DecodeThread");
                decodeThread.start();
            }

            // Receive loop on this port
            byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
            int timeouts = 0;
            int handshakeRetries = 0;

            while (streaming) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    ds.receive(packet);
                    int length = packet.getLength();
                    totalPackets++;
                    totalBytes += length;
                    timeouts = 0;

                    if (!dataReceived) {
                        dataReceived = true;
                        handler.post(() -> progressBar.setVisibility(View.GONE));
                        appendLog("I", ">>> 数据到达! port=" + port + " len=" + length + " from=" + packet.getAddress());
                    }

                    if (totalPackets <= 20) {
                        String hex = bytesToHexFull(buffer, Math.min(length, 64));
                        appendLog("D", "PKT#" + totalPackets + " len=" + length + " [" + hex + "]");
                    }

                    // Parse RTP
                    if (length >= RTP_HEADER_MIN_SIZE) {
                        int version = (buffer[0] >> 6) & 0x03;
                        if (version == 2) {
                            int payloadType = buffer[1] & 0x7F;
                            int seqNum = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                            int ts = ((buffer[4] & 0xFF) << 24) | ((buffer[5] & 0xFF) << 16) | ((buffer[6] & 0xFF) << 8) | (buffer[7] & 0xFF);
                            int ssrc = ((buffer[8] & 0xFF) << 24) | ((buffer[9] & 0xFF) << 16) | ((buffer[10] & 0xFF) << 8) | (buffer[11] & 0xFF);
                            if (totalPackets <= 5) {
                                appendLog("D", String.format("RTP: PT=%d Seq=%d TS=%d SSRC=0x%08X", payloadType, seqNum, ts, ssrc));
                            }
                            parseRtpPacket(buffer, length);
                        } else if (totalPackets <= 10) {
                            appendLog("W", "非RTP: V=" + version + " first=0x" + String.format("%02X", buffer[0] & 0xFF));
                        }
                    }

                    // Stats every 100 packets
                    if (totalPackets % 100 == 0) {
                        long now = System.currentTimeMillis();
                        double rate = totalBytes * 1000.0 / Math.max(1, now - lastFpsTime);
                        appendLog("I", "[STATS] pkts=" + totalPackets + " " + String.format("%.0f", rate) + " B/s port=" + port);
                    }

                } catch (SocketTimeoutException e) {
                    timeouts++;
                    appendLog("D", "[RECV] UDP:" + port + " 超时#" + timeouts);
                    if (!dataReceived) {
                        if (timeouts >= 2) {
                            handshakeRetries++;
                            if (handshakeRetries >= 2) {
                                appendLog("I", "[RECV] UDP:" + port + " 2次重试无数据, 切换下一个端口");
                                return false;
                            }
                            sendHandshakeOnSocket(ds, handshake, "UDP:" + port + " 重发#" + handshakeRetries);
                        }
                    }
                } catch (PortUnreachableException e) {
                    appendLog("W", "[RECV] UDP:" + port + " ICMP端口不可达(端口关闭)");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            appendLog("W", "[RECV] UDP:" + port + " 异常: " + e.getMessage());
            return false;
        } finally {
            if (ds != null) try { ds.close(); } catch (Exception ignored) {}
        }
    }

    private void sendHandshakeOnSocket(DatagramSocket ds, byte[] data, String label) {
        try {
            DatagramPacket pkt = new DatagramPacket(data, data.length);
            ds.send(pkt);
            String hex = bytesToHexFull(data, data.length);
            appendLog("I", "[" + label + "] 已发 {" + hex + "}");
        } catch (Exception e) {
            appendLog("W", "[" + label + "] 发送失败: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private static String bytesToHexFull(byte[] data, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X", data[i] & 0xFF));
            if (i < len - 1) sb.append(" ");
        }
        return sb.toString();
    }

    // =========================================================================
    // TCP control connection (port 4646)
    // =========================================================================

    private void connectTcp() {
        try {
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(config.ip, config.tcpPort), 3000);
            tcpSocket.setSoTimeout(5000);
            tcpSocket.setKeepAlive(true);
            tcpSocket.setTcpNoDelay(true);
            appendLog("I", "[TCP] 已连接: " + config.ip + ":" + config.tcpPort);

            BufferedReader reader = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            tcpSocket.setSoTimeout(3000);
            try {
                String firstLine = reader.readLine();
                if (firstLine != null) {
                    appendLog("I", "[TCP] 响应: " + firstLine);
                    try {
                        JSONObject resp = new JSONObject(firstLine);
                        String fw = resp.optString("FirmWare", "");
                        String platform = resp.optString("platform", "");
                        appendLog("I", "[TCP] 固件=" + fw + " 平台=" + platform);
                    } catch (Exception ignored) {}
                }
            } catch (SocketTimeoutException ste) {
                appendLog("W", "[TCP] 读取超时(无初始数据)");
            }

            tcpSocket.setSoTimeout(0);
            tcpThread = new Thread(() -> {
                try {
                    while (streaming) {
                        String line = reader.readLine();
                        if (line == null) break;
                        appendLog("D", "[TCP] " + line);
                    }
                } catch (Exception e) {
                    if (streaming) appendLog("W", "[TCP] 中断: " + e.getMessage());
                }
            }, "TCPThread");
            tcpThread.start();
        } catch (Exception e) {
            appendLog("W", "[TCP] 连接失败: " + e.getMessage());
            tcpSocket = null;
        }
    }

    // =========================================================================
    // RTP packet parsing (RFC 3550)
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
            int extLen = ((data[headerSize + 2] & 0xFF) << 8) | (data[headerSize + 3] & 0xFF);
            headerSize += 4 + (extLen * 4);
        }
        if (headerSize >= length) return;

        int payloadOffset = headerSize;
        int payloadLength = length - headerSize;
        if (padding == 1 && payloadLength > 0) payloadLength -= (data[length - 1] & 0xFF);
        if (payloadLength <= 0) return;

        if (totalPackets <= 5) {
            appendLog("D", String.format("RTP: PT=%d Seq=%d M=%d Pay=%d", payloadType, seqNum, marker, payloadLength));
        }

        int nalHeaderByte = data[payloadOffset] & 0xFF;
        int nalType = nalHeaderByte & 0x1F;

        if (nalType >= 1 && nalType <= 23) {
            byte[] nalUnit = new byte[payloadLength + 4];
            nalUnit[0] = 0x00; nalUnit[1] = 0x00; nalUnit[2] = 0x00; nalUnit[3] = 0x01;
            System.arraycopy(data, payloadOffset, nalUnit, 4, payloadLength);
            handleNalUnit(nalUnit, marker);
        } else if (nalType == RTP_NAL_STAP_A) {
            parseStapA(data, payloadOffset, payloadLength, marker);
        } else if (nalType == RTP_NAL_FU_A) {
            parseFuA(data, payloadOffset, payloadLength, seqNum, marker);
        } else {
            if (totalPackets <= 10) appendLog("D", "NAL type=" + nalType);
        }
    }

    private void parseStapA(byte[] data, int offset, int length, int marker) {
        int pos = offset + 1;
        while (pos + 2 < offset + length) {
            int nalSize = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;
            if (nalSize <= 0 || pos + nalSize > offset + length) break;
            byte[] nalUnit = new byte[nalSize + 4];
            nalUnit[0] = 0x00; nalUnit[1] = 0x00; nalUnit[2] = 0x00; nalUnit[3] = 0x01;
            System.arraycopy(data, pos, nalUnit, 4, nalSize);
            handleNalUnit(nalUnit, (pos + nalSize >= offset + length) ? marker : 0);
            pos += nalSize;
        }
    }

    private void parseFuA(byte[] data, int offset, int length, int seqNum, int marker) {
        if (length < 2) return;
        int fuIndicator = data[offset] & 0xFF;
        int fuHeader = data[offset + 1] & 0xFF;
        boolean startBit = ((fuHeader >> 7) & 0x01) == 1;
        boolean endBit = ((fuHeader >> 6) & 0x01) == 1;
        int nalType = fuHeader & 0x1F;
        int nalRefIdc = (fuIndicator >> 5) & 0x03;

        int fuPayloadOffset = offset + 2;
        int fuPayloadLength = length - 2;

        if (startBit) {
            fuStarted = true;
            fuSeq = seqNum;
            byte originalNalHeader = (byte) ((nalRefIdc << 5) | nalType);
            fuBuffer = ByteBuffer.allocate(4 + 1 + fuPayloadLength + 60000);
            fuBuffer.put(new byte[]{0x00, 0x00, 0x00, 0x01});
            fuBuffer.put(originalNalHeader);
            fuBuffer.put(data, fuPayloadOffset, fuPayloadLength);
        } else if (fuStarted) {
            if (fuBuffer != null && fuBuffer.remaining() >= fuPayloadLength) {
                fuBuffer.put(data, fuPayloadOffset, fuPayloadLength);
            } else if (fuBuffer != null) {
                ByteBuffer newBuf = ByteBuffer.allocate(fuBuffer.position() + fuPayloadLength + 60000);
                fuBuffer.flip();
                newBuf.put(fuBuffer);
                newBuf.put(data, fuPayloadOffset, fuPayloadLength);
                fuBuffer = newBuf;
            }
            if (endBit) {
                fuStarted = false;
                if (fuBuffer != null) {
                    byte[] nalUnit = new byte[fuBuffer.position()];
                    fuBuffer.flip();
                    fuBuffer.get(nalUnit);
                    fuBuffer = null;
                    handleNalUnit(nalUnit, marker);
                }
            }
        }
    }

    // =========================================================================
    // NAL unit handling + MediaCodec decoder
    // =========================================================================

    private void handleNalUnit(byte[] nalUnit, int marker) {
        if (nalUnit.length < 5) return;
        int nalHeader = nalUnit[4] & 0xFF;
        int nalType = nalHeader & 0x1F;

        if (nalType == NAL_TYPE_SPS) {
            spsData = new byte[nalUnit.length - 4];
            System.arraycopy(nalUnit, 4, spsData, 0, spsData.length);
            appendLog("I", ">>> SPS! size=" + spsData.length);
            tryConfigureDecoder();
            return;
        }
        if (nalType == NAL_TYPE_PPS) {
            ppsData = new byte[nalUnit.length - 4];
            System.arraycopy(nalUnit, 4, ppsData, 0, ppsData.length);
            appendLog("I", ">>> PPS! size=" + ppsData.length);
            tryConfigureDecoder();
            return;
        }
        if (nalType == NAL_TYPE_SEI) return;

        if (decoderConfigured) {
            if (nalQueue.remainingCapacity() == 0) nalQueue.poll();
            nalQueue.offer(nalUnit);
        }
    }

    private void tryConfigureDecoder() {
        if (decoderConfigured || spsData == null || ppsData == null) return;
        handler.post(() -> {
            if (decoderConfigured) return;
            int[][] resolutions = {{1280, 720}, {960, 720}, {856, 480}, {640, 480}, {1920, 1080}};
            for (int[] res : resolutions) {
                try {
                    MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, res[0], res[1]);
                    format.setByteBuffer("csd-0", ByteBuffer.wrap(spsData));
                    format.setByteBuffer("csd-1", ByteBuffer.wrap(ppsData));
                    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_UDP_PACKET_SIZE);
                    decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                    decoder.configure(format, surfaceHolder.getSurface(), null, 0);
                    decoder.start();
                    decoderConfigured = true;
                    appendLog("I", ">>> MediaCodec! " + res[0] + "x" + res[1]);
                    return;
                } catch (Exception e) {
                    if (decoder != null) { try { decoder.release(); } catch (Exception ignored) {} decoder = null; }
                }
            }
        });
    }

    private void decoderLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (streaming || !nalQueue.isEmpty()) {
            try {
                byte[] nalUnit = nalQueue.poll();
                if (nalUnit == null) { Thread.sleep(5); continue; }
                if (!decoderConfigured || decoder == null) continue;

                int inputBufIdx = decoder.dequeueInputBuffer(10000);
                if (inputBufIdx >= 0) {
                    ByteBuffer inputBuf = decoder.getInputBuffer(inputBufIdx);
                    if (inputBuf != null) {
                        inputBuf.clear();
                        inputBuf.put(nalUnit);
                        int flags = 0;
                        int nalType = (nalUnit[4] & 0xFF) & 0x1F;
                        if (nalType == NAL_TYPE_IDR) flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
                        decoder.queueInputBuffer(inputBufIdx, 0, nalUnit.length, System.nanoTime() / 1000, flags);
                    }
                }

                int outputBufIdx = decoder.dequeueOutputBuffer(info, 5000);
                if (outputBufIdx >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        frameCount++;
                        long now = System.currentTimeMillis();
                        if (now - lastFpsTime >= 2000) {
                            int fps = (int) (frameCount * 1000.0 / (now - lastFpsTime));
                            final String status = fps + " FPS | " + totalPackets + " pkts";
                            handler.post(() -> tvStatus.setText(status));
                            frameCount = 0;
                            lastFpsTime = now;
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufIdx, true);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat fmt = decoder.getOutputFormat();
                    appendLog("I", "[CODEC] " + fmt.getInteger(MediaFormat.KEY_WIDTH) + "x" + fmt.getInteger(MediaFormat.KEY_HEIGHT));
                }
            } catch (InterruptedException e) { break; }
            catch (Exception e) {
                appendLog("E", "[DECODE] " + e.getMessage());
                if (decoder != null) { try { decoder.stop(); } catch (Exception ignored) {} releaseDecoder(); }
                try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
            }
        }
        appendLog("I", "[DECODE] 线程退出");
    }

    private void releaseDecoder() {
        decoderConfigured = false;
        if (decoder != null) { try { decoder.stop(); decoder.release(); } catch (Exception ignored) {} decoder = null; }
    }

    // =========================================================================
    // Connection management
    // =========================================================================

    private void stopStreaming() {
        streaming = false;
        if (streamThread != null) { streamThread.interrupt(); streamThread = null; }
        if (tcpThread != null) { tcpThread.interrupt(); tcpThread = null; }
        closeTcp(); closeUdp();
        handler.post(() -> { if (progressBar != null) progressBar.setVisibility(View.GONE); });
    }

    private void closeTcp() {
        if (tcpSocket != null) { try { tcpSocket.close(); } catch (Exception ignored) {} tcpSocket = null; }
    }

    private void closeUdp() {
        if (udpSocket != null) { try { udpSocket.close(); } catch (Exception ignored) {} udpSocket = null; }
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
                String fn = "M8_" + System.currentTimeMillis() + ".jpg";
                android.provider.MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, fn, "M8 drone");
                Toast.makeText(this, "照片已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "无画面", Toast.LENGTH_SHORT).show();
            }
            surfaceView.setDrawingCacheEnabled(false);
        } catch (Exception e) {
            surfaceView.setDrawingCacheEnabled(false);
            Toast.makeText(this, "失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // TCP commands
    // =========================================================================

    private void sendTcpCommand(String cmd) {
        appendLog("I", "[CMD] " + cmd);
        new Thread(() -> {
            Socket socket = null;
            try {
                socket = tcpSocket;
                boolean reuse = (socket != null && socket.isConnected() && !socket.isClosed());
                if (!reuse) {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(config.ip, config.tcpPort), 3000);
                    socket.setSoTimeout(3000);
                }
                JSONObject jsonCmd = new JSONObject();
                jsonCmd.put("CMD", getCommandCode(cmd));
                jsonCmd.put("PARAM", "");
                OutputStream os = socket.getOutputStream();
                os.write((jsonCmd.toString() + "\n").getBytes("UTF-8"));
                os.flush();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String response = reader.readLine();
                appendLog("D", "[CMD] 响应: " + response);
                if (!reuse) socket.close();
                handler.post(() -> Toast.makeText(this, getCommandLabel(cmd) + " OK", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                appendLog("E", "[CMD] 失败: " + e.getMessage());
                if (socket != null && socket != tcpSocket) try { socket.close(); } catch (Exception ignored) {}
                handler.post(() -> Toast.makeText(this, getCommandLabel(cmd) + " 失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private int getCommandCode(String cmd) {
        switch (cmd) { case "takeoff": return 1; case "land": return 2; case "emergency": return 3; case "speed": return 10; default: return 0; }
    }
    private String getCommandLabel(String cmd) {
        switch (cmd) { case "takeoff": return "起飞"; case "land": return "降落"; case "emergency": return "紧急停止"; case "speed": return "变速"; default: return cmd; }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) takeSnapshot();
            else Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show();
        }
    }
}
