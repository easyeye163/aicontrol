package com.aicontrol.android.aircam.h8;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipData;
import android.content.ClipboardManager;

import com.aicontrol.android.R;
import com.aicontrol.android.aircam.base.BaseActivity;
import com.aicontrol.android.aircam.view.SurfaceViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * H8 无人机视频播放主界面 v0.0.103
 *
 * v0.0.103 变更:
 * - LOG 旁新增 COPY 按钮: 一键导出全部日志到剪切板
 * - 日志区域长按可选全选复制 (textIsSelectable)
 * - UDP 增加详细诊断日志 (socket创建/绑定/握手/超时)
 * - UDP 超时从 3s 改为 2s (更快检测无数据)
 * - 每5s周期性报告UDP接收统计
 * - CMD:94/116 fire-and-forget (v0.0.102延续)
 *
 * 协议流程:
 * 1. TCP:4646 连接 → 等待 banner (CMD:0)
 * 2. CMD:94 fire-and-forget (绑定 UDP 端口)
 * 3. CMD:2 等响应 (开启视频预览)
 * 4. CMD:116 fire-and-forget (TCP 通知)
 * 5. 启动 UDP:1563 → D8 C0 D9 握手
 * 6. RTP/H.264 → 去包化 → 解码 → 渲染
 */
public class H8PlayActivity extends BaseActivity
        implements H8TcpObserver, H8UdpVideoThread.H8UdpVideoCallback, H8VideoDecoder.H8DecoderCallback, SurfaceHolder.Callback {

    private static final String TAG = "H8Play";

    // ======================== UI 组件 ========================

    private SurfaceViews mSurfaceView;
    private TextView mTvTitle;
    private TextView mTvStatus;
    private Button mBtnConnect;
    private Button mBtnStart;
    private Button mBtnStop;
    private Button mBtnCamera;
    private Button mBtnLog;
    private Button mBtnCopy;
    private TextView mTvLog;
    private ScrollView mLogContainer;

    // ======================== 核心组件 ========================

    private H8TcpManager mTcpManager;
    private H8UdpVideoThread mUdpVideoThread;
    private H8RtpDepacketizer mRtpDepacketizer;
    private H8VideoDecoder mVideoDecoder;

    // ======================== 状态标志 ========================

    /** TCP 是否已连接 */
    private volatile boolean mTcpConnected = false;

    /** 固件信息字符串 */
    private String mFirmwareInfo = "";

    /** 视频流是否正在接收 */
    private volatile boolean mStreaming = false;

    /** 帧计数器 (调试用) */
    private int mFrameCount = 0;

    /** 摄像头状态: false=后置(默认), true=前置 */
    private boolean mFrontCamera = false;

    /** 视频激活是否完成 */
    private volatile boolean mVideoActivated = false;

    /** 视频渲染 Surface (来自 SurfaceViews 的 SurfaceHolder) */
    private volatile Surface mVideoSurface = null;

    /** 日志时间格式 */
    private final SimpleDateFormat mLogTimeFormat =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    /** 日志全量文本 (用于导出剪切板) */
    private final StringBuilder mFullLogText = new StringBuilder();

    // ======================== 生命周期 ========================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        // 设置全屏
        getWindow().addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_h8_play);
        initViews();
        initComponents();

        // 自动连接 TCP (与原版 H8 一致: TcpManager 作为 Service 自动连接)
        connectTcp();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy - 释放所有资源");
        stopStreaming();
        disconnectTcp();
    }

    // ======================== UI 初始化 ========================

    private void initViews() {
        mSurfaceView = (SurfaceViews) findViewById(R.id.h8_surface_video);
        mTvTitle = (TextView) findViewById(R.id.h8_tv_title);
        mTvStatus = (TextView) findViewById(R.id.h8_tv_status);
        mBtnConnect = (Button) findViewById(R.id.h8_btn_connect);
        mBtnStart = (Button) findViewById(R.id.h8_btn_start);
        mBtnStop = (Button) findViewById(R.id.h8_btn_stop);
        mBtnCamera = (Button) findViewById(R.id.h8_btn_camera);
        mBtnLog = (Button) findViewById(R.id.h8_btn_log);
        mBtnCopy = (Button) findViewById(R.id.h8_btn_copy);
        mTvLog = (TextView) findViewById(R.id.h8_tv_log);
        mLogContainer = (ScrollView) findViewById(R.id.h8_log_container);

        // 连接按钮
        mBtnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mTcpConnected) {
                    disconnectTcp();
                } else {
                    connectTcp();
                }
            }
        });

        // 开始按钮 - 手动触发完整协议序列
        mBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startStreaming();
            }
        });

        // 停止按钮
        mBtnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopStreaming();
            }
        });

        // 摄像头切换按钮
        mBtnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera();
            }
        });

        // LOG 按钮: 单击 = 切换日志显示/隐藏
        mBtnLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLog();
            }
        });

        // COPY 按钮: 一键导出全部日志到剪切板
        mBtnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportLog();
            }
        });

        // 初始状态
        mBtnStart.setEnabled(false);
        mBtnStop.setEnabled(false);
        mBtnCamera.setEnabled(false);
        updateStatus("未连接");
    }

    private void initComponents() {
        mTcpManager = new H8TcpManager();
        mRtpDepacketizer = new H8RtpDepacketizer();
        mVideoDecoder = new H8VideoDecoder();

        // 注册 SurfaceHolder 回调，获取视频渲染 Surface
        mSurfaceView.getHolder().addCallback(this);
    }

    // ======================== TCP 连接管理 ========================

    /**
     * 连接 TCP 控制通道
     */
    private void connectTcp() {
        appendLog("正在连接 TCP " + H8Constants.DRONE_IP + ":" + H8Constants.TCP_PORT + " ...");
        updateStatus("正在连接...");

        mTcpManager.registerObserver(this);
        mTcpManager.connectAsync();

        mBtnConnect.setText("断开");
    }

    /**
     * 断开 TCP 控制通道
     */
    private void disconnectTcp() {
        if (mTcpManager != null) {
            mTcpManager.unregisterObserver(this);
            mTcpManager.disconnect();
        }
        mTcpConnected = false;
        mVideoActivated = false;
        updateStatus("未连接");
        mBtnConnect.setText("连接");
        mBtnStart.setEnabled(false);
        mBtnCamera.setEnabled(false);
        appendLog("TCP 已断开");
    }

    // ======================== 视频激活 (v0.0.99 核心) ========================

    /**
     * v0.0.102: 视频激活序列 (优化版)
     *
     * v0.0.101 问题: CMD:94 和 CMD:116 超时阻塞 3s+2s=5s，
     *   二进制握手探索可能扰乱无人机协议状态机
     *
     * v0.0.102 策略:
     * - 二进制握手探索移除 (不再自动执行)
     * - CMD:94 fire-and-forget: 发送但不等响应
     * - CMD:2 等响应: 这是唯一需要确认的命令
     * - CMD:116 fire-and-forget: 发送但不等响应
     * - CMD:2 成功后立即启动 UDP，不浪费时间
     */
    private void activateVideoStream() {
        if (!mTcpConnected || !mTcpManager.isBannerReceived()) {
            appendLog("激活失败: TCP 未连接或未收到 banner");
            return;
        }

        new Thread("H8Activate") {
            @Override
            public void run() {
                appendLog("========== v0.0.103 视频激活序列 ==========");

                // === Step 1: CMD:94 fire-and-forget (绑定 UDP 端口) ===
                // v0.0.102: 不等待响应，避免 3s 超时阻塞
                appendLog("[CMD] 发送 CMD:94 (绑定UDP端口, fire-and-forget)...");
                mTcpManager.sendCommand(H8Constants.Command.CMD_BIND_APP_UDP, "1563");
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}

                // === Step 2: CMD:2 等响应 (开启视频预览) ===
                // 这是核心命令，必须确认成功
                appendLog("[CMD] 发送 CMD:2 (开启视频预览)...");
                H8TcpManager.H8CommandResult r2 = mTcpManager.sendCommandAndWait(
                        2, H8Constants.Command.VID_ENC_PREVIEW_ON, "", 3000
                );
                appendLog("[CMD] CMD:2 响应: " + r2);

                if (r2.isTimeout()) {
                    appendLog("[CMD] CMD:2 超时，重试...");
                    H8TcpManager.H8CommandResult r2r = mTcpManager.sendCommandAndWait(
                            2, H8Constants.Command.VID_ENC_PREVIEW_ON, "", 3000
                    );
                    appendLog("[CMD] CMD:2 重试: " + r2r);

                    if (r2r.isTimeout()) {
                        appendLog("[ACTIVATE] CMD:2 两次超时，放弃激活");
                        return;
                    }
                }

                // === Step 3: CMD:116 fire-and-forget (TCP连接通知) ===
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                appendLog("[CMD] 发送 CMD:116 (TCP连接通知, fire-and-forget)...");
                mTcpManager.sendCommand(H8Constants.Command.CMD_TCP_CONNECTED, "");

                // === Step 4: 立即启动 UDP ===
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}

                mVideoActivated = true;
                appendLog("[ACTIVATE] ★ 激活序列完成，启动 UDP 接收");

                startUdpVideo();

                // === Step 5: 周期性检查是否收到视频 ===
                for (int retry = 1; retry <= 3 && mStreaming; retry++) {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    if (!mStreaming) break;
                    if (mFrameCount > 0) {
                        appendLog("[CHECK] 第" + retry + "次检查: 已收到 " + mFrameCount + " 帧 ✓");
                        break;
                    }
                    appendLog("[RETRY] 第" + retry + "次: 5秒未收到视频帧，重试...");
                    mTcpManager.sendCommand(H8Constants.Command.VID_ENC_PREVIEW_ON, "");
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    mTcpManager.sendCommand(H8Constants.Command.CMD_BIND_APP_UDP, "1563");
                }
                if (mStreaming && mFrameCount == 0) {
                    appendLog("[WARN] 3次重试后仍未收到视频帧");
                    appendLog("[HINT] 请用COPY按钮导出日志，检查UDP连接状态");
                }
            }
        }.start();
    }

    /**
     * 探索二进制握手协议
     *
     * v0.0.98 日志发现:
     * - 发送 D8 C0 D9 → 收到 16B (EC 0D B4 94 ...)
     * - 发送 01 00 00 00 → 收到 16B (5E E5 98 DB ...)
     * - 发送 02 00 00 00 → 收到 16B (92 14 59 72 ...)
     * 所有响应都是 16 字节且每次不同 — 可能是 challenge-response 认证
     *
     * 策略: 发送 D8 C0 D9 → 收到 16B → 直接回传这 16B → 看是否完成握手
     */
    private void exploreBinaryHandshake() {
        try {
            // 发送 UDP 握手魔数 D8 C0 D9
            byte[] handshake = H8Constants.HANDSHAKE_MAGIC;
            byte[] response = mTcpManager.sendRawBytesAndWait(handshake, 2000);

            if (response == null) {
                appendLog("[BINARY] D8 C0 D9 无响应");
                return;
            }

            String hex = bytesToHex(response);
            appendLog("[BINARY] D8 C0 D9 → " + response.length + "B: " + hex);

            // 尝试直接回传收到的数据 (echo-back 策略)
            appendLog("[BINARY] 尝试回传收到的 16B...");
            byte[] echoResponse = mTcpManager.sendRawBytesAndWait(response, 2000);

            if (echoResponse != null) {
                appendLog("[BINARY] 回传响应: " + echoResponse.length + "B: " + bytesToHex(echoResponse));
            } else {
                appendLog("[BINARY] 回传无响应");
            }

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            // 尝试发送 01 00 00 00 看响应模式
            byte[] ping1 = {0x01, 0x00, 0x00, 0x00};
            byte[] resp1 = mTcpManager.sendRawBytesAndWait(ping1, 1000);
            if (resp1 != null) {
                appendLog("[BINARY] 01 00 00 00 → " + resp1.length + "B: " + bytesToHex(resp1));
            }

            // 尝试发送 4 字节 0 (空 ping)
            byte[] ping0 = {0x00, 0x00, 0x00, 0x00};
            byte[] resp0 = mTcpManager.sendRawBytesAndWait(ping0, 1000);
            if (resp0 != null) {
                appendLog("[BINARY] 00 00 00 00 → " + resp0.length + "B: " + bytesToHex(resp0));
            }

        } catch (Exception e) {
            appendLog("[BINARY] 探索异常: " + e.getMessage());
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] data) {
        if (data == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, 64); i++) {
            sb.append(String.format("%02X ", data[i] & 0xFF));
        }
        if (data.length > 64) sb.append("... (" + data.length + "B total)");
        return sb.toString().trim();
    }

    // ======================== 视频流管理 ========================

    /**
     * 开始接收视频流 (触发完整协议序列)
     */
    private void startStreaming() {
        if (!mTcpConnected) {
            appendLog("错误: TCP 未连接，无法开始视频流");
            return;
        }

        if (mStreaming) {
            appendLog("视频流已在运行");
            return;
        }

        if (!mTcpManager.isBannerReceived()) {
            appendLog("等待 banner，稍后自动开始...");
            return;
        }

        appendLog("启动视频流...");

        // 初始化 RTP 去包化器
        mRtpDepacketizer.reset();

        // v0.0.101: 将 SurfaceViews 的 Surface 传递给解码器，启用直接渲染
        // 这样 MediaCodec 会直接将解码帧渲染到 SurfaceView，无需 Bitmap 中转
        if (mVideoSurface != null && !mVideoSurface.isValid()) {
            mVideoSurface = null;
        }
        if (mVideoSurface != null) {
            mVideoDecoder.setSurface(mVideoSurface);
            appendLog("[DECODER] 使用 Surface 直接渲染模式");
        } else {
            appendLog("[DECODER] Surface 未就绪，使用 Bitmap 回退模式");
        }

        // 创建并启动解码器
        mVideoDecoder.setCallback(this);
        mVideoDecoder.start();

        // v0.0.99: 先执行完整协议序列，再启动 UDP
        activateVideoStream();
    }

    /**
     * 启动 UDP 视频接收线程
     * 必须在 activateVideoStream() 完成后调用
     */
    private void startUdpVideo() {
        if (mStreaming) {
            appendLog("UDP 接收已在运行");
            return;
        }

        // 创建并启动 UDP 视频接收线程
        mUdpVideoThread = new H8UdpVideoThread(this);
        mUdpVideoThread.start();

        mStreaming = true;
        mFrameCount = 0;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBtnStart.setEnabled(false);
                mBtnStop.setEnabled(true);
                mBtnCamera.setEnabled(true);
                updateStatus("视频流接收中");
            }
        });
    }

    /**
     * 停止视频流
     */
    private void stopStreaming() {
        if (!mStreaming) return;

        appendLog("停止视频流...");

        // 停止 UDP 线程
        if (mUdpVideoThread != null) {
            mUdpVideoThread.stop();
            mUdpVideoThread = null;
        }

        // 停止解码器
        if (mVideoDecoder != null) {
            mVideoDecoder.stop();
        }

        // 重置 RTP 去包化器
        mRtpDepacketizer.reset();

        // 发送 CMD:3 关闭视频预览
        if (mTcpConnected && mVideoActivated) {
            mTcpManager.sendCommand(H8Constants.Command.VID_ENC_PREVIEW_OFF, "");
            appendLog("已发送 CMD:3 (关闭视频预览)");
        }

        mStreaming = false;
        mFrameCount = 0;

        mBtnStart.setEnabled(mTcpConnected && mTcpManager.isBannerReceived());
        mBtnStop.setEnabled(false);
        if (mTcpConnected) {
            updateStatus("已连接 - 待命");
        }

        appendLog("视频流已停止");
    }

    // ======================== 摄像头切换 ========================

    /**
     * 切换前后摄像头
     *
     * HFun 无人机协议中，摄像头切换通过 CMD:20 实现:
     * - PARAM="" 或 PARAM="0" = 后置摄像头
     * - PARAM="1" = 前置摄像头
     *
     * 注意: 这是基于协议分析的推测，可能需要根据实际 APK 行为调整
     */
    private void switchCamera() {
        if (!mTcpConnected) {
            appendLog("切换摄像头失败: TCP 未连接");
            return;
        }

        mFrontCamera = !mFrontCamera;
        String param = mFrontCamera ? "1" : "0";
        String cameraName = mFrontCamera ? "前置" : "后置";

        appendLog("切换到 " + cameraName + " 摄像头 (CMD:20 PARAM=" + param + ")");
        mTcpManager.sendCommand(H8Constants.Command.RESOLUTION_SET, param);

        updateStatus("视频流 - " + cameraName + "摄像头");
    }

    // ======================== H8TcpObserver 实现 ========================

    @Override
    public void onTcpConnected() {
        Log.d(TAG, "TCP 已连接");
        mTcpConnected = true;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendLog("✓ TCP 已连接，等待服务器推送 banner...");
                updateStatus("已连接 - 等待 banner");
                mBtnConnect.setText("断开");
            }
        });
    }

    @Override
    public void onTcpDisconnected() {
        Log.d(TAG, "TCP 已断开");
        mTcpConnected = false;
        mVideoActivated = false;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendLog("✗ TCP 已断开");
                updateStatus("连接断开");
                mBtnConnect.setText("连接");
                mBtnStart.setEnabled(false);
                mBtnStop.setEnabled(false);
                mBtnCamera.setEnabled(false);

                // 如果正在流传输，停止
                if (mStreaming) {
                    stopStreaming();
                }
            }
        });
    }

    @Override
    public void onTcpCommand(int cmd, int result, String param) {
        Log.d(TAG, "TCP 命令响应: CMD=" + cmd + " RESULT=" + result + " PARAM=" + param);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                H8Constants.Command command = H8Constants.Command.fromCode(cmd);
                String cmdName = command != null ? command.name() : "UNKNOWN(" + cmd + ")";

                appendLog("← CMD:" + cmdName + " R:" + result + " P:" + param);

                // 处理固件 banner (CMD=0) - 服务器主动推送
                if (cmd == 0 && result == 0 && !param.isEmpty()) {
                    mFirmwareInfo = param;
                    appendLog("★ 收到固件 banner: " + param);
                    parseFirmwareInfo(param);

                    // v0.0.99: 收到 banner 后只启用"开始"按钮，不自动启动
                    // 用户可以手动点击"开始"触发完整协议序列
                    // 这样可以在日志中清楚看到每个步骤的结果
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mBtnStart.setEnabled(true);
                            updateStatus("已连接 - 就绪");
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onTcpData(byte[] data) {
        // 原始二进制数据回调 (调试用)
        if (data.length > 0 && data.length < 100) {
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(data.length, 32); i++) {
                hex.append(String.format("%02X ", data[i] & 0xFF));
            }
            Log.d(TAG, "TCP 二进制数据: " + data.length + "B hex: " + hex);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appendLog("← 二进制数据: " + data.length + "B");
                }
            });
        }
    }

    // ======================== H8UdpVideoCallback 实现 ========================

    /** UDP 接收统计 */
    private int mUdpPacketCount = 0;
    private long mUdpLastStatTime = 0;

    @Override
    public void onUdpVideoData(byte[] data, int length) {
        if (length <= 0 || !mStreaming) return;

        mUdpPacketCount++;

        // 每100包输出统计
        if (mUdpPacketCount % 100 == 0) {
            long now = System.currentTimeMillis();
            long elapsed = now - mUdpLastStatTime;
            if (mUdpLastStatTime > 0 && elapsed > 0) {
                final int pkts = mUdpPacketCount;
                final float rate = pkts * 1000f / elapsed;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("[UDP] 收到 " + pkts + " 包, " + String.format("%.1f", rate) + " 包/秒");
                    }
                });
            }
            mUdpLastStatTime = now;
        }

        try {
            byte[] nalUnit = mRtpDepacketizer.parseRtpPacket(data, length);
            if (nalUnit != null && nalUnit.length > 0) {
                mVideoDecoder.offerData(nalUnit);
            }
        } catch (Exception e) {
            Log.w(TAG, "处理 UDP 数据异常: " + e.getMessage());
        }
    }

    @Override
    public void onFirstFrame() {
        mUdpLastStatTime = System.currentTimeMillis();
        Log.d(TAG, "收到第一帧 UDP 视频数据");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendLog("✓ 收到第一帧 UDP 视频数据!");
                updateStatus("视频流接收中 - OK");
            }
        });
    }

    /** v0.0.103: UDP 诊断日志回调 */
    @Override
    public void onUdpLog(String message) {
        appendLog(message);
    }

    // ======================== H8DecoderCallback 实现 ========================

    @Override
    public void onFrameDecoded(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;

        mFrameCount++;

        // 每 100 帧输出一次日志
        if (mFrameCount % 100 == 0) {
            Log.d(TAG, "已解码 " + mFrameCount + " 帧 (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ")");
        }

        // 渲染到 SurfaceViews
        final Bitmap frame = bitmap;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSurfaceView != null) {
                    try {
                        mSurfaceView.SetBitmap(frame);
                    } catch (Exception e) {
                        Log.w(TAG, "渲染 Bitmap 失败: " + e.getMessage());
                    }
                }
            }
        });
    }

    @Override
    public void onDecoderInfo(int width, int height) {
        Log.d(TAG, "解码器信息: " + width + "x" + height);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendLog("视频分辨率: " + width + "x" + height);
                updateStatus("视频流 " + width + "x" + height);
            }
        });
    }

    // ======================== 固件信息解析 ========================

    /**
     * 解析固件信息字符串
     * 格式可能是:
     * - 纯字符串: "H8-720P-6-1-1-2-00020003-2-1-1-4"
     * - 嵌套 JSON: {"FirmWare":"1.0.5","platform":"A7-720P",...}
     */
    private void parseFirmwareInfo(String firmwareStr) {
        try {
            // 先尝试解析为嵌套 JSON
            if (firmwareStr.startsWith("{")) {
                try {
                    org.json.JSONObject paramJson = new org.json.JSONObject(firmwareStr);
                    String firmware = paramJson.optString("FirmWare", "");
                    String platform = paramJson.optString("platform", "");
                    if (!firmware.isEmpty()) {
                        appendLog("固件版本: " + firmware);
                    }
                    if (!platform.isEmpty()) {
                        appendLog("平台: " + platform);
                        parseResolution(platform);
                    }
                    return;
                } catch (Exception ignored) {}
            }

            // 纯字符串格式: "H8-720P-6-1-1-2-00020003-2-1-1-4"
            String[] parts = firmwareStr.split("-");
            if (parts.length < 2) return;

            String platform = parts[0]; // "H8"
            String resolution = parts[1]; // "720P"

            appendLog("平台: " + platform + ", 分辨率: " + resolution);
            parseResolution(resolution);

        } catch (Exception e) {
            Log.w(TAG, "解析固件信息失败: " + e.getMessage());
        }
    }

    /**
     * 解析分辨率字符串
     */
    private void parseResolution(String resStr) {
        // 从嵌套字符串如 "A7-720P" 中提取分辨率部分
        if (resStr.contains("-")) {
            String[] parts = resStr.split("-");
            for (String part : parts) {
                if (part.toUpperCase().contains("P") || part.toUpperCase().contains("K")) {
                    resStr = part;
                    break;
                }
            }
        }

        H8Constants.Resolution res = parseResolutionEnum(resStr);
        if (res != null) {
            appendLog("分辨率枚举: " + res.name());
        }
    }

    /**
     * 解析分辨率字符串为枚举
     */
    private H8Constants.Resolution parseResolutionEnum(String resStr) {
        switch (resStr.toUpperCase()) {
            case "VGA":
                return H8Constants.Resolution.VGA;
            case "QVGA":
                return H8Constants.Resolution.QVGA;
            case "720P":
            case "HD720P":
                return H8Constants.Resolution.HD_720P;
            case "1080P":
            case "FHD":
                return H8Constants.Resolution.FULL_HD_1080P;
            case "2K":
            case "QHD":
                return H8Constants.Resolution.QHD_2K;
            case "4K":
            case "UHD":
                return H8Constants.Resolution.UHD_4K;
            default:
                return null;
        }
    }

    // ======================== UI 辅助方法 ========================

    /**
     * 更新状态栏文字
     */
    private void updateStatus(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mTvStatus != null) {
                    mTvStatus.setText(text);
                }
            }
        });
    }

    /**
     * 追加日志到日志区域 (同时记录到 mFullLogText 用于导出)
     */
    private void appendLog(final String text) {
        final String timestamp = mLogTimeFormat.format(new Date());
        final String logLine = "[" + timestamp + "] " + text + "\n";

        Log.d(TAG, "LOG: " + text);

        // 全量记录 (用于导出剪切板，不受500行限制)
        synchronized (mFullLogText) {
            mFullLogText.append(logLine);
            // 防止内存溢出，限制 50KB
            if (mFullLogText.length() > 50000) {
                String full = mFullLogText.toString();
                int cut = full.indexOf('\n', 10000);
                if (cut > 0) {
                    mFullLogText.delete(0, cut + 1);
                }
            }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mTvLog != null) {
                    mTvLog.append(logLine);
                    // 限制屏幕日志长度 (最多 500 行)
                    if (mTvLog.getLineCount() > 500) {
                        CharSequence cs = mTvLog.getText();
                        int start = 0;
                        for (int i = 0; i < 100; i++) {
                            int idx = cs.toString().indexOf('\n', start);
                            if (idx < 0) break;
                            start = idx + 1;
                        }
                        mTvLog.setText(cs.subSequence(start, cs.length()));
                    }
                    // 自动滚动到底部
                    if (mLogContainer != null) {
                        mLogContainer.post(new Runnable() {
                            @Override
                            public void run() {
                                mLogContainer.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                    }
                }
            }
        });
    }

    // ======================== SurfaceHolder.Callback 实现 ========================

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mVideoSurface = holder.getSurface();
        appendLog("[SURFACE] Surface 已创建: " + mVideoSurface);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        appendLog("[SURFACE] Surface 变更: " + width + "x" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        appendLog("[SURFACE] Surface 已销毁");
        mVideoSurface = null;
    }

    /**
     * 切换日志显示/隐藏
     */
    private void toggleLog() {
        if (mLogContainer != null) {
            int visibility = mLogContainer.getVisibility() == View.VISIBLE
                    ? View.GONE : View.VISIBLE;
            mLogContainer.setVisibility(visibility);
        }
    }

    /**
     * 导出日志内容到剪贴板 (长按 LOG 按钮触发)
     */
    private void exportLog() {
        String logText = mFullLogText.toString();
        if (logText.isEmpty()) {
            logText = mTvLog != null ? mTvLog.getText().toString() : "";
        }
        if (logText.isEmpty()) {
            Toast.makeText(this, "日志为空", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("H8 Drone Log", logText);
        clipboard.setPrimaryClip(clip);

        appendLog("★ 日志已复制到剪贴板 (" + logText.length() + " 字符)");
        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }
}
