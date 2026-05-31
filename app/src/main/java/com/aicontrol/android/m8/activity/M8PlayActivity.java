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
import android.net.NetworkInfo;
import android.net.Uri;
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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
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
 *
 * Video pipeline:
 * 1. TCP:4646 connect (verify reachability)
 * 2. UDP:1563 handshake {0xD8, 0xC0, 0xD9}
 * 3. Receive H264 RTP -> parse/depacketize -> MediaCodec decode -> SurfaceView
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
    private int consecutiveTimeouts = 0;

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
        // Log WiFi info on every resume
        logNetworkDiagnostics();
        // Auto-start streaming
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

        // Back button
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Log toggle button
        ImageButton btnLog = findViewById(R.id.btnLog);
        btnLog.setOnClickListener(v -> {
            int vis = lyLogPanel.getVisibility();
            lyLogPanel.setVisibility(vis == View.GONE ? View.VISIBLE : View.GONE);
        });

        // Config button
        ImageButton btnConfig = findViewById(R.id.btnConfig);
        btnConfig.setOnClickListener(v -> {
            stopStreaming();
            startActivity(new Intent(this, M8ConfigActivity.class));
        });

        // Clear log
        TextView btnClearLog = findViewById(R.id.btnClearLog);
        btnClearLog.setOnClickListener(v -> {
            logBuilder.setLength(0);
            logCount.set(0);
            tvLogContent.setText("");
            tvLogCount.setText("0");
        });

        // Go to config
        com.aicontrol.android.widget.KButton btnGoConfig = findViewById(R.id.btnGoConfig);
        btnGoConfig.setOnClickListener(v -> startActivity(new Intent(this, M8ConfigActivity.class)));

        // Connect/reconnect button
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
        long startMs = System.currentTimeMillis();

        // 1. WiFi SSID
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String ssid = wifiInfo.getSSID();
            int rssi = wifiInfo.getRssi();
            int linkSpeed = wifiInfo.getLinkSpeed();
            String macAddr = wifiInfo.getMacAddress();
            int networkId = wifiInfo.getNetworkId();
            int ipInt = wifiInfo.getIpAddress();
            String localIp = intToIp(ipInt);
            appendLog("I", "[WiFi] SSID=" + ssid + " RSSI=" + rssi + "dBm LinkSpeed=" + linkSpeed + "Mbps");
            appendLog("I", "[WiFi] 本机IP=" + localIp + " NetworkID=" + networkId + " MAC=" + macAddr);
        } catch (Exception e) {
            appendLog("W", "[WiFi] 获取WiFi信息失败: " + e.getMessage());
        }

        // 2. DHCP Gateway
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
            String gateway = intToIp(dhcpInfo.gateway);
            String netmask = intToIp(dhcpInfo.netmask);
            String dns1 = intToIp(dhcpInfo.dns1);
            String server = intToIp(dhcpInfo.serverAddress);
            appendLog("I", "[DHCP] Gateway=" + gateway + " Netmask=" + netmask + " DNS=" + dns1 + " Server=" + server);

            // Check if gateway matches drone IP
            if (gateway.startsWith("192.168.100.")) {
                appendLog("I", "[DHCP] 网关在192.168.100.x网段, 可能已连接无人机WiFi");
            } else {
                appendLog("W", "[DHCP] 网关=" + gateway + " 不在无人机网段(192.168.100.x)");
            }
        } catch (Exception e) {
            appendLog("W", "[DHCP] 获取DHCP信息失败: " + e.getMessage());
        }

        // 3. All network interfaces
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    appendLog("I", "[NetIF] " + ni.getName() + " MTU=" + ni.getMTU());
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (addr instanceof Inet4Address) {
                            appendLog("I", "[NetIF]   IPv4: " + addr.getHostAddress());
                        }
                    }
                }
            }
        } catch (Exception e) {
            appendLog("W", "[NetIF] 获取网络接口失败: " + e.getMessage());
        }

        // 4. Check connectivity to drone IP
        appendLog("I", "[PING] 检查连通性 " + config.ip + " ...");
        try {
            long t1 = System.currentTimeMillis();
            boolean reachable = InetAddress.getByName(config.ip).isReachable(3000);
            long t2 = System.currentTimeMillis();
            appendLog("I", "[PING] " + config.ip + " reachable=" + reachable + " 耗时=" + (t2 - t1) + "ms");
        } catch (Exception e) {
            appendLog("W", "[PING] 连通性检查失败: " + e.getMessage());
        }

        // 5. Check if drone IP is in the same subnet
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
            int gw = dhcpInfo.gateway;
            int localInt = dhcpInfo.ipAddress;
            String gwStr = intToIp(gw);
            String localStr = intToIp(localInt);
            appendLog("I", "[SUBNET] 本机=" + localStr + " 网关=" + gwStr + " 目标=" + config.ip);

            // Simple subnet check: compare first 3 octets
            String[] gwParts = gwStr.split("\\.");
            String[] targetParts = config.ip.split("\\.");
            boolean sameSubnet = gwParts.length >= 3 && targetParts.length >= 3
                    && gwParts[0].equals(targetParts[0])
                    && gwParts[1].equals(targetParts[1])
                    && gwParts[2].equals(targetParts[2]);
            appendLog("I", "[SUBNET] 同一子网: " + sameSubnet + " (网关前3段=" + gwParts[0] + "." + gwParts[1] + "." + gwParts[2] + ")");
        } catch (Exception e) {
            appendLog("W", "[SUBNET] 子网检查失败: " + e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - startMs;
        appendLog("I", "===== 网络诊断完成 (" + elapsed + "ms) =====");
    }

    private static String intToIp(int addr) {
        return ((addr & 0xFF) + "." +
                ((addr >> 8) & 0xFF) + "." +
                ((addr >> 16) & 0xFF) + "." +
                ((addr >> 24) & 0xFF));
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
            // Keep max 800 lines
            String text = logBuilder.toString();
            int lineCount = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') lineCount++;
                if (lineCount > 800) {
                    text = text.substring(text.indexOf('\n', i) + 1);
                    logBuilder.setLength(0);
                    logBuilder.append(text);
                    break;
                }
            }
            tvLogContent.setText(logBuilder.toString());
            svLogScroll.post(() -> svLogScroll.fullScroll(ScrollView.FOCUS_DOWN));
            int c = logCount.incrementAndGet();
            tvLogCount.setText(String.valueOf(c));
        });
    }

    // =========================================================================
    // Video stream connection
    // =========================================================================

    private void startStreaming() {
        if (streaming) return;

        streaming = true;
        dataReceived = false;
        consecutiveTimeouts = 0;
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

        appendLog("I", "========== 开始连接 (v0.0.89-diag) ==========");
        appendLog("I", "配置: IP=" + config.ip + " UDP=" + config.udpPort + " TCP=" + config.tcpPort + " FTP=" + config.ftpPort);

        streamThread = new Thread(() -> {
            try {
                // Run network diagnostics on worker thread (more accurate)
                logNetworkDiagnosticsWorker();

                // 1. TCP connect to verify reachability
                appendLog("I", "[STEP1] TCP连接 " + config.ip + ":" + config.tcpPort + " ...");
                long tcpStart = System.currentTimeMillis();
                connectTcp();
                long tcpElapsed = System.currentTimeMillis() - tcpStart;
                appendLog("I", "[STEP1] TCP结果: " + (tcpSocket != null ? "成功(" + tcpElapsed + "ms)" : "失败(" + tcpElapsed + "ms)"));

                // 2. Create UDP socket
                appendLog("I", "[STEP2] 创建UDP socket...");
                long udpStart = System.currentTimeMillis();
                udpSocket = new DatagramSocket(null);
                udpSocket.setReuseAddress(true);
                udpSocket.bind(new InetSocketAddress(0)); // bind to any available port
                int localPort = udpSocket.getLocalPort();
                udpSocket.setSoTimeout(3000);
                udpSocket.setReceiveBufferSize(MAX_UDP_PACKET_SIZE * 10);
                udpSocket.setSendBufferSize(65536);
                long udpElapsed = System.currentTimeMillis() - udpStart;
                appendLog("I", "[STEP2] UDP socket已创建: 本地端口=" + localPort + " sendBuf=" + udpSocket.getSendBufferSize() + " recvBuf=" + udpSocket.getReceiveBufferSize() + " (" + udpElapsed + "ms)");

                // 3. Send handshake (try multiple times with different approaches)
                appendLog("I", "[STEP3] 发送握手包...");

                // Approach 1: Original 3-byte handshake
                byte[] handshake1 = new byte[]{(byte) 0xD8, (byte) 0xC0, (byte) 0xD9};
                sendHandshake(handshake1, "方案A: 3字节 {D8 C0 D9}");

                // Approach 2: Try with connection (connect socket)
                try {
                    udpSocket.connect(new InetSocketAddress(config.ip, config.udpPort));
                    sendHandshake(handshake1, "方案B: connect后发送");
                    udpSocket.disconnect();
                } catch (Exception e) {
                    appendLog("W", "方案B失败: " + e.getMessage());
                }

                // Approach 3: 4-byte handshake (maybe needs length prefix?)
                byte[] handshake3 = new byte[]{0x00, (byte) 0xD8, (byte) 0xC0, (byte) 0xD9};
                sendHandshake(handshake3, "方案C: 4字节 {00 D8 C0 D9}");

                // Approach 4: Empty UDP packet to port
                try {
                    DatagramPacket emptyPkt = new DatagramPacket(new byte[0], 0, InetAddress.getByName(config.ip), config.udpPort);
                    udpSocket.send(emptyPkt);
                    appendLog("I", "方案D: 空包已发送");
                } catch (Exception e) {
                    appendLog("W", "方案D失败: " + e.getMessage());
                }

                // Approach 5: Re-connect and resend original handshake
                try {
                    udpSocket.connect(new InetSocketAddress(config.ip, config.udpPort));
                    sendHandshake(handshake1, "方案E: reconnect后再发 {D8 C0 D9}");
                } catch (Exception e) {
                    appendLog("W", "方案E失败: " + e.getMessage());
                }

                handler.post(() -> {
                    tvStatus.setText("握手已发送(5种方案) | 等待数据...");
                });

                // 4. Start decoder thread
                Thread decodeThread = new Thread(this::decoderLoop, "DecodeThread");
                decodeThread.start();

                // 5. Receive loop
                appendLog("I", "[STEP4] 进入接收循环, timeout=3000ms...");
                byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];

                int handshakeRetryCount = 0;
                long lastHandshakeRetry = System.currentTimeMillis();

                while (streaming) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);
                        int length = packet.getLength();
                        totalPackets++;
                        totalBytes += length;
                        consecutiveTimeouts = 0;

                        if (!dataReceived) {
                            dataReceived = true;
                            long elapsed = System.currentTimeMillis() - lastHandshakeRetry;
                            handler.post(() -> {
                                progressBar.setVisibility(View.GONE);
                            });
                            appendLog("I", ">>> 首包到达! len=" + length + " 来自=" + packet.getAddress().getHostAddress() + ":" + packet.getPort());
                            appendLog("I", ">>> 距上次握手: " + elapsed + "ms, 总握手尝试=" + handshakeRetryCount);
                        }

                        // Log EVERY packet for the first 20
                        if (totalPackets <= 20) {
                            String hex = bytesToHexFull(buffer, Math.min(length, 64));
                            appendLog("D", "UDP#" + totalPackets + " len=" + length + " from=" + packet.getAddress().getHostAddress() + ":" + packet.getPort()
                                    + " [" + hex + "]");
                        }

                        // Check if it looks like RTP
                        if (length >= RTP_HEADER_MIN_SIZE) {
                            int version = (buffer[0] >> 6) & 0x03;
                            int payloadType = buffer[1] & 0x7F;
                            int seqNum = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                            int timestamp = ((buffer[4] & 0xFF) << 24) | ((buffer[5] & 0xFF) << 16) | ((buffer[6] & 0xFF) << 8) | (buffer[7] & 0xFF);
                            int ssrc = ((buffer[8] & 0xFF) << 24) | ((buffer[9] & 0xFF) << 16) | ((buffer[10] & 0xFF) << 8) | (buffer[11] & 0xFF);

                            if (totalPackets <= 5) {
                                appendLog("D", String.format("RTP: V=%d PT=%d Seq=%d TS=%d SSRC=0x%08X", version, payloadType, seqNum, timestamp, ssrc));
                            }

                            if (version == 2) {
                                parseRtpPacket(buffer, length);
                            } else if (totalPackets <= 20) {
                                appendLog("W", "非RTP包! V=" + version + " firstByte=0x" + String.format("%02X", buffer[0] & 0xFF));
                            }
                        } else if (length > 0 && totalPackets <= 20) {
                            appendLog("D", "短包(len=" + length + "): " + bytesToHexFull(buffer, length));
                        }

                        // Periodic stats
                        if (totalPackets % 100 == 0) {
                            long now = System.currentTimeMillis();
                            double rate = totalBytes * 1000.0 / Math.max(1, now - lastFpsTime);
                            appendLog("I", "[STATS] pkts=" + totalPackets + " bytes=" + totalBytes + " queue=" + nalQueue.size() + " rate=" + String.format("%.0f", rate) + " B/s");
                        }

                        // Re-send handshake every 3 seconds if no data received yet
                        if (!dataReceived && System.currentTimeMillis() - lastHandshakeRetry > 3000 && handshakeRetryCount < 10) {
                            handshakeRetryCount++;
                            lastHandshakeRetry = System.currentTimeMillis();
                            appendLog("I", "[RETRY#" + handshakeRetryCount + "] 重发握手包...");
                            sendHandshake(handshake1, "重试#" + handshakeRetryCount);
                        }

                    } catch (SocketTimeoutException e) {
                        consecutiveTimeouts++;
                        if (!dataReceived && streaming) {
                            if (consecutiveTimeouts >= 2 && handshakeRetryCount == 0) {
                                // After 2 timeouts without any retry yet
                                appendLog("W", "超时#" + consecutiveTimeouts + " 未收到数据, 重发握手...");
                                handshakeRetryCount++;
                                lastHandshakeRetry = System.currentTimeMillis();
                                sendHandshake(handshake1, "超时重发#" + handshakeRetryCount);
                            }
                            if (consecutiveTimeouts >= 5) {
                                appendLog("E", "等待数据超时! 连续" + consecutiveTimeouts + "次超时");
                                appendLog("E", "诊断: 请确认 1)手机已连接H8/M8 WiFi热点 2)无人机已开机 3)IP=" + config.ip + " 4)端口=" + config.udpPort);
                                handler.post(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    lyNotConnected.setVisibility(View.VISIBLE);
                                    tvStatus.setText("超时 - 未收到数据");
                                    if (btnConnect != null) btnConnect.setVisibility(View.VISIBLE);
                                });
                                stopStreaming();
                                return;
                            }
                            appendLog("D", "超时#" + consecutiveTimeouts + " 继续等待...");
                        } else if (streaming) {
                            appendLog("D", "接收超时 (已收到数据, stream可能中断)");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Stream error", e);
                appendLog("E", "连接异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                Log.e(TAG, "Stack:", e);
                // Log full stack trace
                for (StackTraceElement ste : e.getStackTrace()) {
                    appendLog("E", "  at " + ste.toString());
                }
                handler.post(() -> {
                    if (streaming) {
                        progressBar.setVisibility(View.GONE);
                        lyNotConnected.setVisibility(View.VISIBLE);
                        tvStatus.setText("连接失败: " + e.getMessage());
                        if (btnConnect != null) btnConnect.setVisibility(View.VISIBLE);
                    }
                });
            } finally {
                closeUdp();
                streaming = false;
                appendLog("I", "========== 连接结束 (总包=" + totalPackets + " 总字节=" + totalBytes + ") ==========");
            }
        }, "StreamThread");
        streamThread.start();
    }

    private void logNetworkDiagnosticsWorker() {
        // Worker thread network checks
        appendLog("I", "[WORKER] 线程网络诊断...");
        try {
            // Check TCP port reachability
            Socket testSocket = null;
            try {
                testSocket = new Socket();
                testSocket.connect(new InetSocketAddress(config.ip, config.tcpPort), 2000);
                appendLog("I", "[WORKER] TCP:" + config.tcpPort + " 可达!");
                testSocket.close();
            } catch (Exception e) {
                appendLog("W", "[WORKER] TCP:" + config.tcpPort + " 不可达: " + e.getMessage());
                if (testSocket != null) try { testSocket.close(); } catch (Exception ignored) {}
            }

            // Check UDP port with a probe
            DatagramSocket probeSocket = null;
            try {
                probeSocket = new DatagramSocket();
                probeSocket.setSoTimeout(2000);
                byte[] probe = new byte[]{(byte) 0xD8, (byte) 0xC0, (byte) 0xD9};
                DatagramPacket probePkt = new DatagramPacket(probe, probe.length, InetAddress.getByName(config.ip), config.udpPort);
                long t1 = System.currentTimeMillis();
                probeSocket.send(probePkt);
                appendLog("I", "[WORKER] UDP探测包已发送到 " + config.ip + ":" + config.udpPort + " (" + (System.currentTimeMillis() - t1) + "ms)");
                // Try to receive response
                byte[] recvBuf = new byte[1024];
                DatagramPacket recvPkt = new DatagramPacket(recvBuf, recvBuf.length);
                try {
                    probeSocket.receive(recvPkt);
                    appendLog("I", "[WORKER] UDP收到响应! len=" + recvPkt.getLength() + " from=" + recvPkt.getAddress() + ":" + recvPkt.getPort());
                    appendLog("I", "[WORKER] 响应数据: " + bytesToHexFull(recvBuf, Math.min(recvPkt.getLength(), 32)));
                } catch (SocketTimeoutException te) {
                    appendLog("W", "[WORKER] UDP 2秒内无响应 (不一定有问题, 可能需要特定握手序列)");
                }
            } catch (Exception e) {
                appendLog("W", "[WORKER] UDP探测失败: " + e.getMessage());
            } finally {
                if (probeSocket != null) try { probeSocket.close(); } catch (Exception ignored) {}
            }

            // Check other common drone ports
            int[] portsToCheck = {8554, 8080, 554, 5000, 5001, 80, 443, 8866, 9100};
            for (int port : portsToCheck) {
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(config.ip, port), 500);
                    appendLog("I", "[PORT] " + config.ip + ":" + port + " 开放!");
                    s.close();
                } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            appendLog("W", "[WORKER] 诊断异常: " + e.getMessage());
        }
    }

    private void sendHandshake(byte[] data, String label) {
        try {
            DatagramPacket pkt;
            if (udpSocket.isConnected()) {
                pkt = new DatagramPacket(data, data.length);
            } else {
                pkt = new DatagramPacket(data, data.length, InetAddress.getByName(config.ip), config.udpPort);
            }
            long t1 = System.nanoTime();
            udpSocket.send(pkt);
            long elapsed = (System.nanoTime() - t1) / 1_000_000;
            String hex = bytesToHexFull(data, data.length);
            appendLog("I", "[" + label + "] 已发送 " + data.length + "字节 {" + hex + "} -> " + config.ip + ":" + config.udpPort + " (" + elapsed + "ms)");
        } catch (Exception e) {
            appendLog("E", "[" + label + "] 发送失败: " + e.getMessage());
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

    private static String bytesToHex(byte[] data, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X ", data[i] & 0xFF));
            if (i >= 16) { sb.append("..."); break; }
        }
        return sb.toString().trim();
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
            appendLog("I", "[TCP] local=" + tcpSocket.getLocalAddress().getHostAddress() + ":" + tcpSocket.getLocalPort()
                    + " remote=" + tcpSocket.getRemoteSocketAddress()
                    + " keepAlive=" + tcpSocket.getKeepAlive()
                    + " tcpNoDelay=" + tcpSocket.getTcpNoDelay());

            // Read initial response with timeout
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(tcpSocket.getInputStream()));
            tcpSocket.setSoTimeout(3000);
            try {
                String firstLine = reader.readLine();
                if (firstLine != null) {
                    appendLog("I", "[TCP] 收到响应 (" + firstLine.length() + "字节): " + firstLine);
                    try {
                        JSONObject resp = new JSONObject(firstLine);
                        String fw = resp.optString("FirmWare", "");
                        String platform = resp.optString("platform", "");
                        int result = resp.optInt("RESULT", -1);
                        appendLog("I", "[TCP] 固件=" + fw + " 平台=" + platform + " RESULT=" + result);

                        // Try sending a query command
                        appendLog("I", "[TCP] 发送查询指令...");
                        JSONObject query = new JSONObject();
                        query.put("CMD", 0);
                        query.put("PARAM", "");
                        OutputStream os = tcpSocket.getOutputStream();
                        os.write((query.toString() + "\n").getBytes("UTF-8"));
                        os.flush();
                        appendLog("I", "[TCP] 查询已发: " + query.toString());

                        tcpSocket.setSoTimeout(2000);
                        String queryResp = reader.readLine();
                        if (queryResp != null) {
                            appendLog("I", "[TCP] 查询响应: " + queryResp);
                        }
                    } catch (Exception je) {
                        appendLog("W", "[TCP] JSON解析: " + je.getMessage());
                    }
                    handler.post(() -> tvStatus.setText("TCP已连接 | UDP握手中..."));
                } else {
                    appendLog("W", "[TCP] 响应为空");
                }
            } catch (SocketTimeoutException ste) {
                appendLog("W", "[TCP] 读取响应超时(3s), 可能无初始数据");
            }

            // Start TCP reader thread
            tcpSocket.setSoTimeout(0); // Infinite for reader thread
            tcpThread = new Thread(() -> {
                try {
                    while (streaming) {
                        String line = reader.readLine();
                        if (line == null) break;
                        appendLog("D", "[TCP-RECV] " + line);
                    }
                } catch (Exception e) {
                    if (streaming) appendLog("W", "[TCP] 读取中断: " + e.getMessage());
                }
            }, "TCPThread");
            tcpThread.start();
        } catch (Exception e) {
            appendLog("W", "[TCP] 连接失败(非致命): " + e.getClass().getSimpleName() + " " + e.getMessage());
            appendLog("W", "[TCP] 继续尝试UDP视频流...");
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
        if (padding == 1 && payloadLength > 0) {
            payloadLength -= (data[length - 1] & 0xFF);
        }
        if (payloadLength <= 0) return;

        if (totalPackets <= 5) {
            appendLog("D", String.format("RTP parse: PT=%d Seq=%d M=%d Payload=%d Offset=%d",
                    payloadType, seqNum, marker, payloadLength, payloadOffset));
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
            if (totalPackets <= 10) {
                appendLog("D", "NAL类型=" + nalType + " (STAP-A=" + RTP_NAL_STAP_A + " FU-A=" + RTP_NAL_FU_A + ") len=" + payloadLength);
            }
        }
    }

    private void parseStapA(byte[] data, int offset, int length, int marker) {
        appendLog("D", "STAP-A聚合包 len=" + length);
        int pos = offset + 1;
        while (pos + 2 < offset + length) {
            int nalSize = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;
            if (nalSize <= 0 || pos + nalSize > offset + length) break;
            byte[] nalUnit = new byte[nalSize + 4];
            nalUnit[0] = 0x00; nalUnit[1] = 0x00; nalUnit[2] = 0x00; nalUnit[3] = 0x01;
            System.arraycopy(data, pos, nalUnit, 4, nalSize);
            int nalType = (nalUnit[4] & 0xFF) & 0x1F;
            appendLog("D", "STAP-A NAL: type=" + nalType + " size=" + nalSize);
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
            if (totalPackets <= 15) {
                appendLog("D", "FU-A START: type=" + nalType + " refIdc=" + nalRefIdc + " seq=" + seqNum + " payload=" + fuPayloadLength);
            }
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
                    if (totalPackets <= 15) {
                        int nalT = (nalUnit[4] & 0xFF) & 0x1F;
                        appendLog("D", "FU-A END: type=" + nalT + " totalSize=" + nalUnit.length + " (from seq " + fuSeq + " to " + seqNum + ")");
                    }
                    handleNalUnit(nalUnit, marker);
                }
            }
        } else {
            // FU-A fragment without START - maybe missed start
            if (totalPackets <= 10) {
                appendLog("W", "FU-A碎片但无START! type=" + nalType + " seq=" + seqNum);
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
            appendLog("I", ">>> SPS! size=" + spsData.length + " hex=[" + bytesToHexFull(spsData, Math.min(spsData.length, 32)) + "]");
            // Try to parse SPS for resolution
            tryParseSpsResolution(spsData);
            tryConfigureDecoder();
            return;
        }

        if (nalType == NAL_TYPE_PPS) {
            ppsData = new byte[nalUnit.length - 4];
            System.arraycopy(nalUnit, 4, ppsData, 0, ppsData.length);
            appendLog("I", ">>> PPS! size=" + ppsData.length + " hex=[" + bytesToHexFull(ppsData, Math.min(ppsData.length, 16)) + "]");
            tryConfigureDecoder();
            return;
        }

        if (nalType == NAL_TYPE_SEI) {
            if (totalPackets <= 5) appendLog("D", "SEI NAL size=" + nalUnit.length);
            return;
        }

        if (decoderConfigured) {
            if (nalQueue.remainingCapacity() == 0) nalQueue.poll();
            nalQueue.offer(nalUnit);
        } else if (totalPackets <= 20) {
            appendLog("D", "NAL type=" + nalType + " size=" + nalUnit.length + " (解码器未就绪,丢弃)");
        }
    }

    private void tryParseSpsResolution(byte[] sps) {
        // Simple SPS parsing to get width/height
        try {
            if (sps.length < 8) return;
            int idx = 0;
            // Skip forbidden_zero_bit(1), nal_ref_idc(2), nal_unit_type(5) = byte[0]
            // Then profile_idc=byte[1], constraint_set_flags=byte[2], level_idc=byte[3]
            // Then exp-golomb coded seq_parameter_set_id
            // For simplicity, just log the profile and level
            int profile = sps[1] & 0xFF;
            int level = sps[3] & 0xFF;
            appendLog("I", "[SPS] profile=" + profile + " level=" + level + " (66=Baseline, 77=Main, 100=High)");
        } catch (Exception e) {
            appendLog("W", "[SPS] 解析失败: " + e.getMessage());
        }
    }

    private void tryConfigureDecoder() {
        if (decoderConfigured || spsData == null || ppsData == null) return;
        handler.post(() -> {
            if (decoderConfigured) return;
            try {
                // Try different resolutions
                int[][] resolutions = {{1280, 720}, {960, 720}, {856, 480}, {640, 480}, {1920, 1080}};

                for (int[] res : resolutions) {
                    try {
                        MediaFormat format = MediaFormat.createVideoFormat(
                                MediaFormat.MIMETYPE_VIDEO_AVC, res[0], res[1]);
                        format.setByteBuffer("csd-0", ByteBuffer.wrap(spsData));
                        format.setByteBuffer("csd-1", ByteBuffer.wrap(ppsData));
                        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_UDP_PACKET_SIZE);

                        decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                        decoder.configure(format, surfaceHolder.getSurface(), null, 0);
                        decoder.start();
                        decoderConfigured = true;

                        appendLog("I", ">>> MediaCodec启动! " + res[0] + "x" + res[1] + " SPS=" + spsData.length + " PPS=" + ppsData.length);
                        tvStatus.setText("解码器 " + res[0] + "x" + res[1] + " | 等待视频帧...");
                        return;
                    } catch (Exception e) {
                        appendLog("W", "解码器配置 " + res[0] + "x" + res[1] + " 失败: " + e.getMessage());
                        if (decoder != null) {
                            try { decoder.release(); } catch (Exception ignored) {}
                            decoder = null;
                        }
                    }
                }
                appendLog("E", "所有分辨率尝试均失败");
            } catch (Exception e) {
                appendLog("E", "MediaCodec配置异常: " + e.getMessage());
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
                        decoder.queueInputBuffer(inputBufIdx, 0, nalUnit.length,
                                System.nanoTime() / 1000, flags);
                    }
                }

                int outputBufIdx = decoder.dequeueOutputBuffer(info, 5000);
                if (outputBufIdx >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        frameCount++;
                        long now = System.currentTimeMillis();
                        if (now - lastFpsTime >= 2000) {
                            int fps = (int) (frameCount * 1000.0 / (now - lastFpsTime));
                            double bitrate = totalBytes * 1000.0 / Math.max(1, now - lastFpsTime);
                            final String status = config.ip + " | " + fps + " FPS | " + String.format("%.0f", bitrate / 1024) + " KB/s | pkts=" + totalPackets;
                            handler.post(() -> tvStatus.setText(status));
                            appendLog("I", "[DECODE] " + fps + " FPS " + String.format("%.0f", bitrate / 1024) + " KB/s frames=" + frameCount);
                            frameCount = 0;
                            lastFpsTime = now;
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufIdx, true);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    int w = newFormat.getInteger(MediaFormat.KEY_WIDTH);
                    int h = newFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    String mimeType = newFormat.getString(MediaFormat.KEY_MIME);
                    appendLog("I", "[CODEC] 输出格式: " + w + "x" + h + " " + mimeType);
                    handler.post(() -> tvStatus.setText("解码中 " + w + "x" + h));
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    appendLog("D", "[CODEC] output buffers changed");
                }
            } catch (InterruptedException e) { break; }
            catch (Exception e) {
                appendLog("E", "[DECODE] 错误: " + e.getMessage());
                if (decoder != null) { try { decoder.stop(); } catch (Exception ignored) {} releaseDecoder(); }
                try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
            }
        }
        appendLog("I", "[DECODE] 线程退出");
    }

    private void releaseDecoder() {
        decoderConfigured = false;
        if (decoder != null) {
            try { decoder.stop(); decoder.release(); } catch (Exception ignored) {}
            decoder = null;
        }
    }

    // =========================================================================
    // Connection management
    // =========================================================================

    private void stopStreaming() {
        streaming = false;
        if (streamThread != null) { streamThread.interrupt(); streamThread = null; }
        if (tcpThread != null) { tcpThread.interrupt(); tcpThread = null; }
        closeTcp(); closeUdp();
        handler.post(() -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
        });
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            return;
        }
        try {
            surfaceView.setDrawingCacheEnabled(true);
            Bitmap bitmap = surfaceView.getDrawingCache();
            if (bitmap != null) {
                String filename = "M8_" + System.currentTimeMillis() + ".jpg";
                android.provider.MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, filename, "M8/H8 drone photo");
                Toast.makeText(this, "照片已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "没有可保存的画面", Toast.LENGTH_SHORT).show();
            }
            surfaceView.setDrawingCacheEnabled(false);
        } catch (Exception e) {
            surfaceView.setDrawingCacheEnabled(false);
            Toast.makeText(this, "拍照失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // TCP commands
    // =========================================================================

    private void sendTcpCommand(String cmd) {
        appendLog("I", "[CMD] 发送: " + cmd);
        new Thread(() -> {
            Socket socket = null;
            try {
                socket = tcpSocket;
                boolean reuse = (socket != null && socket.isConnected() && !socket.isClosed());
                if (!reuse) {
                    appendLog("D", "[CMD] TCP复用不可用, 新建连接...");
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(config.ip, config.tcpPort), 3000);
                    socket.setSoTimeout(3000);
                    appendLog("D", "[CMD] 新TCP已连接");
                }
                JSONObject jsonCmd = new JSONObject();
                jsonCmd.put("CMD", getCommandCode(cmd));
                jsonCmd.put("PARAM", "");
                OutputStream os = socket.getOutputStream();
                String cmdStr = jsonCmd.toString() + "\n";
                os.write(cmdStr.getBytes("UTF-8"));
                os.flush();
                appendLog("D", "[CMD] 已发: " + cmdStr.trim());

                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String response = reader.readLine();
                appendLog("D", "[CMD] 响应: " + response);
                if (!reuse) socket.close();
                handler.post(() -> Toast.makeText(this, getCommandLabel(cmd) + " OK", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                appendLog("E", "[CMD] 失败: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                if (socket != null && socket != tcpSocket) { try { socket.close(); } catch (Exception ignored) {} }
                handler.post(() -> Toast.makeText(this, getCommandLabel(cmd) + " 失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private int getCommandCode(String cmd) {
        switch (cmd) {
            case "takeoff": return 1;
            case "land": return 2;
            case "emergency": return 3;
            case "speed": return 10;
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) takeSnapshot();
            else Toast.makeText(this, "需要存储权限才能拍照", Toast.LENGTH_SHORT).show();
        }
    }
}
