package com.aicontrol.android.m8.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
 * M8/H8 Drone video stream playback activity with debug log panel.
 *
 * Video pipeline:
 * 1. TCP:4646 connect (verify reachability)
 * 2. UDP:1563 handshake {0xD8, 0xC0, 0xD9}
 * 3. Receive H264 RTP → parse/depacketize → MediaCodec decode → SurfaceView
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
    private Handler handler = new Handler(Looper.getMainLooper());
    private SimpleDateFormat logTimeFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

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
                releaseDecoder();
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
    // Debug Log
    // =========================================================================

    private void appendLog(String level, String msg) {
        String time = logTimeFmt.format(new Date());
        String line = "[" + time + "] " + level + " " + msg + "\n";
        Log.d(TAG, msg);
        handler.post(() -> {
            logBuilder.append(line);
            // Keep max 500 lines
            String text = logBuilder.toString();
            int lineCount = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') lineCount++;
                if (lineCount > 500) {
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
        nalQueue.clear();
        fuStarted = false;
        fuBuffer = null;
        fuSeq = -1;
        decoderConfigured = false;
        spsData = null;
        ppsData = null;
        frameCount = 0;
        totalPackets = 0;

        progressBar.setVisibility(View.VISIBLE);
        lyNotConnected.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("正在连接...");

        appendLog("I", "========== 开始连接 ==========");
        appendLog("I", "配置: IP=" + config.ip + " 视频端口=" + config.udpPort + " 控制端口=" + config.tcpPort + " FTP端口=" + config.ftpPort);

        streamThread = new Thread(() -> {
            try {
                // 1. TCP connect to verify reachability
                appendLog("I", "正在TCP连接 " + config.ip + ":" + config.tcpPort + " ...");
                connectTcp();

                // If TCP failed, we might still be OK (drone might not have TCP server running)
                // The real test is whether we receive UDP data
                appendLog("I", "正在UDP连接 " + config.ip + ":" + config.udpPort + " ...");

                // 2. Connect UDP
                udpSocket = new DatagramSocket();
                udpSocket.setSoTimeout(5000); // 5s timeout for first packet, then 10s
                udpSocket.connect(new InetSocketAddress(config.ip, config.udpPort));
                udpSocket.setReceiveBufferSize(MAX_UDP_PACKET_SIZE * 10);

                appendLog("I", "UDP socket已绑定");

                // 3. Send handshake
                byte[] handshake = new byte[]{(byte) 0xD8, (byte) 0xC0, (byte) 0xD9};
                DatagramPacket handshakePkt = new DatagramPacket(handshake, handshake.length);
                udpSocket.send(handshakePkt);
                appendLog("I", "握手包已发送: {0xD8, 0xC0, 0xD9} → " + config.ip + ":" + config.udpPort);

                handler.post(() -> {
                    tvStatus.setText("握手已发送 | 等待数据...");
                });

                // 4. Start decoder thread
                Thread decodeThread = new Thread(this::decoderLoop, "DecodeThread");
                decodeThread.start();

                // 5. Receive loop - wait for first packet to confirm connection
                byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
                udpSocket.setSoTimeout(5000); // First packet timeout 5s

                while (streaming) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);
                        int length = packet.getLength();
                        totalPackets++;

                        if (!dataReceived) {
                            dataReceived = true;
                            udpSocket.setSoTimeout(10000); // Longer timeout after first packet
                            handler.post(() -> {
                                progressBar.setVisibility(View.GONE);
                            });
                            appendLog("I", ">>> 收到第一个数据包! len=" + length + " 来自 " + packet.getAddress());
                        }

                        if (length > 0 && streaming) {
                            // Log first 5 packets in detail
                            if (totalPackets <= 5) {
                                String hex = bytesToHex(buffer, Math.min(length, 20));
                                appendLog("D", "UDP pkt #" + totalPackets + " len=" + length + " data=" + hex);
                            }

                            // Check if it looks like RTP
                            int version = (buffer[0] >> 6) & 0x03;
                            if (version == 2 && length >= RTP_HEADER_MIN_SIZE) {
                                parseRtpPacket(buffer, length);
                            } else {
                                // Not RTP - log it
                                if (totalPackets <= 20) {
                                    appendLog("W", "非RTP数据包 #" + totalPackets + " len=" + length + " firstByte=0x" + String.format("%02X", buffer[0] & 0xFF));
                                }
                            }
                        }

                        // Log packet count every 100 packets
                        if (totalPackets % 100 == 0) {
                            appendLog("D", "已接收 " + totalPackets + " 个UDP包, queue=" + nalQueue.size());
                        }

                    } catch (SocketTimeoutException e) {
                        if (!dataReceived && streaming) {
                            appendLog("E", "等待数据超时! 未收到任何数据包 (可能未连接无人机WiFi热点)");
                            appendLog("W", "请确认: 1)手机已连接H8/M8 WiFi 2)IP=" + config.ip + " 3)端口=" + config.udpPort);
                            handler.post(() -> {
                                progressBar.setVisibility(View.GONE);
                                lyNotConnected.setVisibility(View.VISIBLE);
                                tvStatus.setText("未收到数据 - 请检查WiFi连接");
                            });
                            stopStreaming();
                            return;
                        } else if (streaming) {
                            appendLog("W", "UDP接收超时 (streaming, 无数据中...)");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Stream error", e);
                appendLog("E", "连接异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
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
                appendLog("I", "========== 连接结束 ==========");
            }
        }, "StreamThread");
        streamThread.start();
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
            appendLog("I", "TCP已连接: " + config.ip + ":" + config.tcpPort);

            // Read initial response
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(tcpSocket.getInputStream()));
            String firstLine = reader.readLine();
            if (firstLine != null) {
                appendLog("I", "TCP响应: " + firstLine);
                try {
                    JSONObject resp = new JSONObject(firstLine);
                    String fw = resp.optString("FirmWare", "");
                    String platform = resp.optString("platform", "");
                    int result = resp.optInt("RESULT", -1);
                    appendLog("I", "固件=" + fw + " 平台=" + platform + " RESULT=" + result);
                    handler.post(() -> tvStatus.setText("TCP已连接 | 固件 " + fw));
                } catch (Exception ignored) {}
            }

            tcpThread = new Thread(() -> {
                try {
                    while (streaming) {
                        String line = reader.readLine();
                        if (line == null) break;
                        appendLog("D", "TCP: " + line);
                    }
                } catch (Exception e) {
                    if (streaming) appendLog("W", "TCP读取中断: " + e.getMessage());
                }
            }, "TCPThread");
            tcpThread.start();
        } catch (Exception e) {
            appendLog("W", "TCP连接失败(非致命): " + e.getMessage());
            appendLog("W", "TCP失败不代表连接不可用，继续尝试UDP视频流...");
            tcpSocket = null;
        }
    }

    // =========================================================================
    // RTP packet parsing (RFC 3550)
    // =========================================================================

    private void parseRtpPacket(byte[] data, int length) {
        int version = (data[0] >> 6) & 0x03;
        if (version != 2) {
            appendLog("W", "无效RTP版本: " + version);
            return;
        }

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

        // Log first RTP packet
        if (totalPackets <= 3) {
            String hex = bytesToHex(data, Math.min(length, 24));
            appendLog("D", String.format("RTP #%d: PT=%d Seq=%d M=%d Payload=%d hex=[%s]",
                    totalPackets, payloadType, seqNum, marker, payloadLength, hex));
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
                appendLog("D", "未知NAL类型: " + nalType + " len=" + payloadLength);
            }
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

            if (totalPackets <= 10) {
                appendLog("D", "FU-A START: nalType=" + nalType + " payload=" + fuPayloadLength);
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

                    if (totalPackets <= 10) {
                        appendLog("D", "FU-A END: NAL size=" + nalUnit.length);
                    }
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
            appendLog("I", "SPS收到, size=" + spsData.length + " hex=" + bytesToHex(spsData, Math.min(spsData.length, 16)));
            tryConfigureDecoder();
            return;
        }

        if (nalType == NAL_TYPE_PPS) {
            ppsData = new byte[nalUnit.length - 4];
            System.arraycopy(nalUnit, 4, ppsData, 0, ppsData.length);
            appendLog("I", "PPS收到, size=" + ppsData.length + " hex=" + bytesToHex(ppsData, Math.min(ppsData.length, 16)));
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
            try {
                MediaFormat format = MediaFormat.createVideoFormat(
                        MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720);
                format.setByteBuffer("csd-0", ByteBuffer.wrap(spsData));
                format.setByteBuffer("csd-1", ByteBuffer.wrap(ppsData));
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_UDP_PACKET_SIZE);

                decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                decoder.configure(format, surfaceHolder.getSurface(), null, 0);
                decoder.start();
                decoderConfigured = true;

                appendLog("I", ">>> MediaCodec H264解码器已启动! SPS=" + spsData.length + " PPS=" + ppsData.length);
                tvStatus.setText("解码器已启动 | 等待视频帧...");
            } catch (Exception e) {
                appendLog("E", "MediaCodec配置失败: " + e.getMessage());
                decoderConfigured = false;
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
                        if (now - lastFpsTime >= 1000) {
                            int fps = (int) (frameCount * 1000.0 / (now - lastFpsTime));
                            final String status = config.ip + " | " + fps + " FPS | pkts=" + totalPackets;
                            handler.post(() -> tvStatus.setText(status));
                            frameCount = 0;
                            lastFpsTime = now;
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufIdx, true);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    int w = newFormat.getInteger(MediaFormat.KEY_WIDTH);
                    int h = newFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    appendLog("I", "输出格式变更: " + w + "x" + h + " " + newFormat);
                    handler.post(() -> tvStatus.setText("解码中 | " + w + "x" + h));
                }
            } catch (InterruptedException e) { break; }
            catch (Exception e) {
                appendLog("E", "解码器错误: " + e.getMessage());
                if (decoder != null) { try { decoder.stop(); } catch (Exception ignored) {} releaseDecoder(); }
                try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
            }
        }
        appendLog("I", "解码线程退出");
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
        appendLog("I", "发送指令: " + cmd);
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
                os.write(jsonCmd.toString().getBytes("UTF-8"));
                os.write("\n".getBytes("UTF-8"));
                os.flush();
                appendLog("D", "指令已发: " + jsonCmd.toString());
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String response = reader.readLine();
                appendLog("D", "指令响应: " + response);
                if (!reuse) socket.close();
                handler.post(() -> Toast.makeText(this, getCommandLabel(cmd) + " 已发送", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                appendLog("E", "指令失败: " + e.getMessage());
                if (socket != null && socket != tcpSocket) { try { socket.close(); } catch (Exception ignored) {} }
                handler.post(() -> Toast.makeText(this, "指令发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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
