package com.aicontrol.android.m8.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * M8/H8 Drone video stream playback activity.
 *
 * Complete video pipeline:
 * 1. Connect UDP:1563 → send 3-byte handshake {0xD8, 0xC0, 0xD9}
 * 2. Receive H264 RTP data from drone
 * 3. Parse RTP header (RFC 3550) + depacketize H264 NAL (RFC 6184)
 * 4. Feed NAL units to MediaCodec for hardware H264 decode
 * 5. Render decoded frames to SurfaceView
 *
 * Protocol from decompiled com.h8 APK (HY-Chip Technology):
 * - Video: UDP 1563, handshake {0xD8, 0xC0, 0xD9}, Live555 RTP H.264
 * - Control: TCP 4646, JSON commands
 * - Telemetry: UDP 19798
 * - FTP: port 21 (user: HY819, pass: 1663819)
 */
public class M8PlayActivity extends com.aicontrol.android.base.BaseActivity {

    private static final String TAG = "M8PlayActivity";
    private static final int STORAGE_PERMISSION_CODE = 1001;
    private static final int MAX_UDP_PACKET_SIZE = 102400; // 100KB (from decompiled UdpThread)
    private static final int RTP_HEADER_MIN_SIZE = 12;

    // H264 NAL unit types
    private static final int NAL_TYPE_SPS = 7;
    private static final int NAL_TYPE_PPS = 8;
    private static final int NAL_TYPE_IDR = 5;
    private static final int NAL_TYPE_SEI = 6;

    // RTP payload types for H264
    private static final int RTP_NAL_STAP_A = 24;  // Single-time aggregation packet
    private static final int RTP_NAL_FU_A = 28;    // Fragmentation unit

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
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

    // MediaCodec
    private MediaCodec decoder;
    private boolean decoderConfigured = false;
    private byte[] spsData = null;
    private byte[] ppsData = null;
    private BlockingQueue<byte[]> nalQueue = new LinkedBlockingQueue<>(60);

    // FPS counter
    private int frameCount = 0;
    private long lastFpsTime = System.currentTimeMillis();

    // FU-A fragmentation state
    private int fuSeq = -1;         // Expected FU-A sequence for current frame
    private ByteBuffer fuBuffer = null;  // Reassembly buffer
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
        tvStatus = findViewById(R.id.tvStatus);

        // SurfaceView callback - create decoder when surface is ready
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "Surface created");
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
                Log.d(TAG, "Surface changed: " + w + "x" + h);
            }
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "Surface destroyed");
                releaseDecoder();
            }
        });

        // Back button
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Config button
        ImageButton btnConfig = findViewById(R.id.btnConfig);
        btnConfig.setOnClickListener(v -> {
            stopStreaming();
            startActivity(new Intent(this, M8ConfigActivity.class));
        });

        // Go to config
        com.aicontrol.android.widget.KButton btnGoConfig = findViewById(R.id.btnGoConfig);
        btnGoConfig.setOnClickListener(v -> {
            startActivity(new Intent(this, M8ConfigActivity.class));
        });

        // Take photo
        ImageButton btnSnap = findViewById(R.id.btnSnap);
        btnSnap.setOnClickListener(v -> takeSnapshot());

        // Speed toggle
        ImageButton btnSpeed = findViewById(R.id.btnSpeed);
        btnSpeed.setOnClickListener(v -> sendTcpCommand("speed"));

        // Emergency stop
        ImageButton btnEmergency = findViewById(R.id.btnEmergency);
        btnEmergency.setOnClickListener(v -> sendTcpCommand("emergency"));

        // Take off
        ImageButton btnTakeOff = findViewById(R.id.btnTakeOff);
        btnTakeOff.setOnClickListener(v -> sendTcpCommand("takeoff"));

        // Land
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
        releaseDecoder();
    }

    // =========================================================================
    // Video stream connection
    // =========================================================================

    /**
     * Connect UDP:1563 → send handshake → receive RTP H264 → MediaCodec decode
     */
    private void startStreaming() {
        if (streaming) return;

        streaming = true;
        nalQueue.clear();
        fuStarted = false;
        fuBuffer = null;
        fuSeq = -1;
        decoderConfigured = false;
        spsData = null;
        ppsData = null;

        progressBar.setVisibility(View.VISIBLE);
        lyNotConnected.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("正在连接 " + config.ip + ":" + config.udpPort + "...");

        streamThread = new Thread(() -> {
            try {
                // 1. Connect TCP control (port 4646) - non-blocking
                connectTcp();

                // 2. Connect UDP video socket (port 1563)
                udpSocket = new DatagramSocket();
                udpSocket.setSoTimeout(10000);
                udpSocket.connect(new InetSocketAddress(config.ip, config.udpPort));
                udpSocket.setReceiveBufferSize(MAX_UDP_PACKET_SIZE * 10);

                // 3. Send UDP handshake: {0xD8, 0xC0, 0xD9} (from decompiled UdpThread)
                byte[] handshake = new byte[]{(byte) 0xD8, (byte) 0xC0, (byte) 0xD9};
                DatagramPacket handshakePkt = new DatagramPacket(handshake, handshake.length);
                udpSocket.send(handshakePkt);
                Log.d(TAG, "UDP handshake sent to " + config.ip + ":" + config.udpPort);

                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("已连接 | 等待视频流...");
                });

                // 4. Start decoder feed thread
                Thread decodeThread = new Thread(this::decoderLoop, "DecodeThread");
                decodeThread.start();

                // 5. Receive RTP packets loop
                byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
                while (streaming) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);
                        int length = packet.getLength();

                        if (length > RTP_HEADER_MIN_SIZE && streaming) {
                            parseRtpPacket(buffer, length);
                        }
                    } catch (SocketTimeoutException e) {
                        if (streaming) {
                            Log.d(TAG, "UDP timeout, waiting for video...");
                        }
                    }
                }
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
                closeUdp();
                streaming = false;
            }
        }, "StreamThread");
        streamThread.start();
    }

    // =========================================================================
    // TCP control connection (port 4646)
    // =========================================================================

    private void connectTcp() {
        try {
            tcpSocket = new Socket();
            tcpSocket.connect(new InetSocketAddress(config.ip, config.tcpPort), 5000);
            tcpSocket.setSoTimeout(5000);
            tcpSocket.setKeepAlive(true);
            Log.d(TAG, "TCP connected: " + config.ip + ":" + config.tcpPort);

            tcpThread = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(tcpSocket.getInputStream()));
                    String line;
                    while (streaming && (line = reader.readLine()) != null) {
                        Log.d(TAG, "TCP: " + line);
                        try {
                            JSONObject resp = new JSONObject(line);
                            Log.d(TAG, "CMD=" + resp.optInt("CMD") + " RESULT=" + resp.optInt("RESULT"));
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    if (streaming) Log.e(TAG, "TCP read error", e);
                }
            }, "TCPThread");
            tcpThread.start();
        } catch (Exception e) {
            Log.e(TAG, "TCP connect failed (non-fatal): " + e.getMessage());
        }
    }

    // =========================================================================
    // RTP packet parsing (RFC 3550)
    // =========================================================================

    /**
     * Parse RTP header and extract H264 NAL payload.
     * RTP header format (12+ bytes):
     *   Byte 0: V(2) P(1) X(1) CC(4)
     *   Byte 1: M(1) PT(7)
     *   Byte 2-3: Sequence Number
     *   Byte 4-7: Timestamp
     *   Byte 8-11: SSRC
     */
    private void parseRtpPacket(byte[] data, int length) {
        // Validate RTP version (V=2, top 2 bits of byte 0)
        int version = (data[0] >> 6) & 0x03;
        if (version != 2) {
            Log.w(TAG, "Invalid RTP version: " + version + ", len=" + length);
            return;
        }

        int padding = (data[0] >> 5) & 0x01;
        int extension = (data[0] >> 4) & 0x01;
        int csrcCount = data[0] & 0x0F;
        int marker = (data[1] >> 7) & 0x01;
        int payloadType = data[1] & 0x7F;
        int seqNum = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

        // Calculate header size
        int headerSize = RTP_HEADER_MIN_SIZE + (csrcCount * 4);

        // Skip extension header if present
        if (extension == 1 && length > headerSize + 4) {
            int extLen = ((data[headerSize + 2] & 0xFF) << 8) | (data[headerSize + 3] & 0xFF);
            headerSize += 4 + (extLen * 4);
        }

        if (headerSize >= length) {
            Log.w(TAG, "RTP header too large: hdr=" + headerSize + " len=" + length);
            return;
        }

        int payloadOffset = headerSize;
        int payloadLength = length - headerSize;

        // Handle padding
        if (padding == 1 && payloadLength > 0) {
            payloadLength -= (data[length - 1] & 0xFF);
        }

        if (payloadLength <= 0) return;

        // Log first few packets for debugging
        if (frameCount == 0) {
            Log.d(TAG, String.format("RTP: PT=%d, Seq=%d, M=%d, PayloadLen=%d, FirstBytes=%02X %02X %02X %02X",
                    payloadType, seqNum, marker, payloadLength,
                    data[payloadOffset] & 0xFF,
                    data[payloadOffset + 1] & 0xFF,
                    data[payloadOffset + 2] & 0xFF,
                    payloadOffset + 3 < length ? data[payloadOffset + 3] & 0xFF : 0));
        }

        // Check if this is H264 RTP payload
        // First byte of H264 RTP payload: forbidden_zero(1) | nal_ref_idc(2) | nal_unit_type(5)
        if (payloadLength < 1) return;

        int nalHeaderByte = data[payloadOffset] & 0xFF;
        int nalType = nalHeaderByte & 0x1F;

        if (nalType >= 1 && nalType <= 23) {
            // Single NAL unit packet (type 1-23)
            byte[] nalUnit = new byte[payloadLength + 4];
            nalUnit[0] = 0x00;
            nalUnit[1] = 0x00;
            nalUnit[2] = 0x00;
            nalUnit[3] = 0x01;
            System.arraycopy(data, payloadOffset, nalUnit, 4, payloadLength);
            handleNalUnit(nalUnit, marker);
        } else if (nalType == RTP_NAL_STAP_A) {
            // STAP-A: aggregation of multiple NAL units
            parseStapA(data, payloadOffset, payloadLength, marker);
        } else if (nalType == RTP_NAL_FU_A) {
            // FU-A: fragmented NAL unit
            parseFuA(data, payloadOffset, payloadLength, seqNum, marker);
        } else {
            // Unknown NAL type - try to pass through
            Log.d(TAG, "Unknown NAL type: " + nalType + ", len=" + payloadLength);
        }
    }

    /**
     * Parse STAP-A (Single-Time Aggregation Packet type 24).
     * Format: STAP-A header(1) | NAL size(2) | NAL data | NAL size(2) | NAL data | ...
     */
    private void parseStapA(byte[] data, int offset, int length, int marker) {
        int pos = offset + 1; // Skip STAP-A header byte
        while (pos + 2 < offset + length) {
            int nalSize = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;
            if (nalSize <= 0 || pos + nalSize > offset + length) break;

            byte[] nalUnit = new byte[nalSize + 4];
            nalUnit[0] = 0x00;
            nalUnit[1] = 0x00;
            nalUnit[2] = 0x00;
            nalUnit[3] = 0x01;
            System.arraycopy(data, pos, nalUnit, 4, nalSize);
            handleNalUnit(nalUnit, (pos + nalSize >= offset + length) ? marker : 0);
            pos += nalSize;
        }
    }

    /**
     * Parse FU-A (Fragmentation Unit type 28).
     * Format: FU indicator(1) | FU header(1) | FU payload
     *   FU indicator: forbidden_zero(1) | nal_ref_idc(2) | type=28(5)
     *   FU header: S(1) | E(1) | R(1) | type(5)
     */
    private void parseFuA(byte[] data, int offset, int length, int seqNum, int marker) {
        if (length < 2) return;

        int fuIndicator = data[offset] & 0xFF;
        int fuHeader = data[offset + 1] & 0xFF;

        boolean startBit = ((fuHeader >> 7) & 0x01) == 1;  // S: start of fragmented NAL
        boolean endBit = ((fuHeader >> 6) & 0x01) == 1;     // E: end of fragmented NAL
        int nalType = fuHeader & 0x1F;                // Original NAL type
        int nalRefIdc = (fuIndicator >> 5) & 0x03;   // NAL reference IDC

        int fuPayloadOffset = offset + 2;
        int fuPayloadLength = length - 2;

        if (startBit) {
            // Start of new fragmented NAL
            fuStarted = true;
            fuSeq = seqNum;

            // Reconstruct original NAL header: forbidden_zero(1) | nal_ref_idc(2) | nal_type(5)
            byte originalNalHeader = (byte) ((nalRefIdc << 5) | nalType);

            // Allocate buffer: start code(4) + NAL header(1) + payload
            // Estimate total size: first fragment payload + potential remaining
            fuBuffer = ByteBuffer.allocate(4 + 1 + fuPayloadLength + 60000);
            fuBuffer.put(new byte[]{0x00, 0x00, 0x00, 0x01});
            fuBuffer.put(originalNalHeader);
            fuBuffer.put(data, fuPayloadOffset, fuPayloadLength);

            Log.d(TAG, "FU-A START: nalType=" + nalType + " refIdc=" + nalRefIdc
                    + " payloadLen=" + fuPayloadLength);
        } else if (fuStarted) {
            // Continuation fragment
            if (fuBuffer != null && fuBuffer.remaining() >= fuPayloadLength) {
                fuBuffer.put(data, fuPayloadOffset, fuPayloadLength);
            } else if (fuBuffer != null) {
                // Buffer too small, grow it
                ByteBuffer newBuf = ByteBuffer.allocate(fuBuffer.position() + fuPayloadLength + 60000);
                fuBuffer.flip();
                newBuf.put(fuBuffer);
                newBuf.put(data, fuPayloadOffset, fuPayloadLength);
                fuBuffer = newBuf;
            }

            if (endBit) {
                // End of fragmented NAL - complete frame
                fuStarted = false;
                if (fuBuffer != null) {
                    byte[] nalUnit = new byte[fuBuffer.position()];
                    fuBuffer.flip();
                    fuBuffer.get(nalUnit);
                    fuBuffer = null;

                    Log.d(TAG, "FU-A END: complete NAL size=" + nalUnit.length + " marker=" + marker);
                    handleNalUnit(nalUnit, marker);
                }
            }
        }
    }

    // =========================================================================
    // NAL unit handling + MediaCodec decoder
    // =========================================================================

    /**
     * Handle a complete NAL unit (with 00 00 00 01 start code).
     */
    private void handleNalUnit(byte[] nalUnit, int marker) {
        if (nalUnit.length < 5) return; // 4 start code + 1 NAL header

        int nalHeader = nalUnit[4] & 0xFF;
        int nalType = nalHeader & 0x1F;

        // Extract SPS/PPS for decoder configuration
        if (nalType == NAL_TYPE_SPS) {
            spsData = new byte[nalUnit.length - 4];
            System.arraycopy(nalUnit, 4, spsData, 0, spsData.length);
            Log.d(TAG, "SPS received, size=" + spsData.length);
            tryConfigureDecoder();
            return;
        }

        if (nalType == NAL_TYPE_PPS) {
            ppsData = new byte[nalUnit.length - 4];
            System.arraycopy(nalUnit, 4, ppsData, 0, ppsData.length);
            Log.d(TAG, "PPS received, size=" + ppsData.length);
            tryConfigureDecoder();
            return;
        }

        // Queue the NAL for decoding (skip SEI)
        if (nalType == NAL_TYPE_SEI) {
            return;
        }

        if (decoderConfigured) {
            // Drop if queue is full (avoid OOM)
            if (nalQueue.remainingCapacity() == 0) {
                nalQueue.poll(); // Drop oldest
            }
            nalQueue.offer(nalUnit);
        }
    }

    /**
     * Try to configure MediaCodec decoder with SPS + PPS.
     */
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

                Log.d(TAG, "MediaCodec H264 decoder started! SPS=" + spsData.length + " PPS=" + ppsData.length);
                tvStatus.setText("已连接 " + config.ip + " | 解码中...");
            } catch (Exception e) {
                Log.e(TAG, "MediaCodec configure failed", e);
                decoderConfigured = false;
            }
        });
    }

    /**
     * Decoder loop: drain NAL queue → feed to MediaCodec.
     */
    private void decoderLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        byte[] outBuffer = new byte[MAX_UDP_PACKET_SIZE + 4];

        while (streaming || !nalQueue.isEmpty()) {
            try {
                byte[] nalUnit = nalQueue.poll();
                if (nalUnit == null) {
                    Thread.sleep(5);
                    continue;
                }

                if (!decoderConfigured || decoder == null) {
                    continue;
                }

                // Feed NAL to decoder
                int inputBufIdx = decoder.dequeueInputBuffer(10000);
                if (inputBufIdx >= 0) {
                    ByteBuffer inputBuf = decoder.getInputBuffer(inputBufIdx);
                    if (inputBuf != null) {
                        inputBuf.clear();
                        inputBuf.put(nalUnit);

                        int flags = 0;
                        int nalType = (nalUnit[4] & 0xFF) & 0x1F;
                        if (nalType == NAL_TYPE_IDR) {
                            flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
                        }
                        decoder.queueInputBuffer(inputBufIdx, 0, nalUnit.length,
                                System.nanoTime() / 1000, flags);
                    }
                }

                // Get decoded output
                int outputBufIdx = decoder.dequeueOutputBuffer(info, 5000);
                if (outputBufIdx >= 0) {
                    // Check if we got a decoded frame (not config)
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        frameCount++;
                        long now = System.currentTimeMillis();
                        if (now - lastFpsTime >= 1000) {
                            int fps = (int) (frameCount * 1000.0 / (now - lastFpsTime));
                            final String status = "已连接 " + config.ip + " | " + fps + " FPS";
                            handler.post(() -> tvStatus.setText(status));
                            frameCount = 0;
                            lastFpsTime = now;
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufIdx, true);
                } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    Log.d(TAG, "Output format changed: " + newFormat);
                }

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Log.e(TAG, "Decoder error", e);
                // Try to reconfigure
                if (decoder != null) {
                    try { decoder.stop(); } catch (Exception ignored) {}
                    releaseDecoder();
                }
                try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
            }
        }

        Log.d(TAG, "Decoder loop exited");
    }

    /**
     * Release MediaCodec decoder resources.
     */
    private void releaseDecoder() {
        decoderConfigured = false;
        if (decoder != null) {
            try {
                decoder.stop();
                decoder.release();
            } catch (Exception e) {
                Log.e(TAG, "Release decoder error", e);
            }
            decoder = null;
        }
    }

    // =========================================================================
    // Connection management
    // =========================================================================

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
            try { tcpSocket.close(); } catch (Exception ignored) {}
            tcpSocket = null;
        }
    }

    private void closeUdp() {
        if (udpSocket != null) {
            try { udpSocket.close(); } catch (Exception ignored) {}
            udpSocket = null;
        }
    }

    // =========================================================================
    // Snapshot
    // =========================================================================

    private void takeSnapshot() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
            return;
        }

        try {
            surfaceView.setDrawingCacheEnabled(true);
            Bitmap bitmap = surfaceView.getDrawingCache();
            if (bitmap != null) {
                String filename = "M8_" + System.currentTimeMillis() + ".jpg";
                android.provider.MediaStore.Images.Media.insertImage(
                        getContentResolver(), bitmap, filename, "M8/H8 drone photo");
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
        Log.d(TAG, "Sending TCP command: " + cmd);
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

                Log.d(TAG, "Command sent: " + jsonCmd.toString());

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                String response = reader.readLine();
                Log.d(TAG, "Response: " + response);

                if (!reuse) socket.close();

                handler.post(() -> {
                    Toast.makeText(this, getCommandLabel(cmd) + " 已发送", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Command failed", e);
                if (socket != null && socket != tcpSocket) {
                    try { socket.close(); } catch (Exception ignored) {}
                }
                handler.post(() -> {
                    Toast.makeText(this, "指令发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takeSnapshot();
            } else {
                Toast.makeText(this, "需要存储权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
