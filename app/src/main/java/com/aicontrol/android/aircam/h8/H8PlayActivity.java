package com.aicontrol.android.aircam.h8;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aicontrol.android.R;
import com.aicontrol.android.aircam.base.BaseActivity;
import com.aicontrol.android.aircam.view.SurfaceViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * H8 无人机视频播放主界面 v0.0.99
 *
 * 修复 v0.0.98 的核心问题:
 * - v0.0.98 缺少 CMD:94 (绑定 UDP 端口) 和 CMD:2 (视频激活) 命令
 * - 导致 UDP:1563 端口始终 ICMP 不可达
 *
 * v0.0.99 正确协议流程:
 * 1. TCP:4646 连接 → 等待服务器推送 banner (CMD:0)
 * 2. 收到 banner → 发 CMD:94 PARAM="1563" (绑定 UDP 端口) → 等响应
 * 3. 发 CMD:2 PARAM="" (开启视频编码预览) → 等响应
 * 4. 发 CMD:116 PARAM="" (TCP 已连接通知) → 等响应
 * 5. 启动 UDP:1563 接收线程 → 发 D8 C0 D9 握手
 * 6. 接收 H264 RTP → 去包化 → 解码 → 渲染
 *
 * 新增功能:
 * - 前后摄像头切换 (CMD:20)
 * - TCP:7070 备选视频通道探测
 * - 完整协议序列同步执行
 *
 * 架构:
 * <pre>
 *   H8PlayActivity
 *     ├── H8TcpManager      (TCP 4646 控制通道)
 *     │     └── H8TcpObserver (本类实现)
 *     ├── H8UdpVideoThread   (UDP 1563 视频接收)
 *     │     └── H8UdpVideoCallback (本类实现)
 *     ├── H8RtpDepacketizer  (RTP/H.264 去包化)
 *     └── H8VideoDecoder     (MediaCodec H.264 解码)
 *           └── H8DecoderCallback (本类实现)
 * </pre>
 */
public class H8PlayActivity extends BaseActivity
        implements H8TcpObserver, H8UdpVideoThread.H8UdpVideoCallback, H8VideoDecoder.H8DecoderCallback {

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

    /** 日志时间格式 */
    private final SimpleDateFormat mLogTimeFormat =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

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

        // 日志切换按钮
        mBtnLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLog();
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
     * v0.0.99: 完整的视频激活序列
     *
     * 必须在后台线程中执行（sendCommandAndWait 是阻塞的）
     *
     * 正确流程:
     * 1. CMD:94 PARAM="1563" — 告诉无人机往 UDP 端口 1563 推视频流
     * 2. CMD:2 PARAM="" — 开启视频编码预览
     * 3. CMD:116 PARAM="" — 通知 TCP 已连接
     */
    private void activateVideoStream() {
        if (!mTcpConnected || !mTcpManager.isBannerReceived()) {
            appendLog("激活失败: TCP 未连接或未收到 banner");
            return;
        }

        new Thread("H8Activate") {
            @Override
            public void run() {
                appendLog("========== v0.0.99 视频激活序列 ==========");

                // Step 1: CMD:94 — 绑定 APP 的 UDP 端口
                appendLog("[ACTIVATE] 发送 CMD:94 (绑定UDP端口 1563)...");
                H8TcpManager.H8CommandResult r94 = mTcpManager.sendCommandAndWait(
                        94, H8Constants.Command.CMD_BIND_APP_UDP, "1563", 3000
                );
                appendLog("[ACTIVATE] CMD:94 响应: " + r94);

                if (r94.isTimeout()) {
                    appendLog("[ACTIVATE] CMD:94 超时，尝试继续...");
                } else if (!r94.isSuccess()) {
                    appendLog("[ACTIVATE] CMD:94 返回 RESULT=" + r94.result + "，尝试继续...");
                } else {
                    appendLog("[ACTIVATE] ★ CMD:94 成功! UDP 端口已绑定");
                }

                // 等待无人机处理
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}

                // Step 2: CMD:2 — 开启视频编码预览
                appendLog("[ACTIVATE] 发送 CMD:2 (开启视频预览)...");
                H8TcpManager.H8CommandResult r2 = mTcpManager.sendCommandAndWait(
                        2, H8Constants.Command.VID_ENC_PREVIEW_ON, "", 3000
                );
                appendLog("[ACTIVATE] CMD:2 响应: " + r2);

                if (r2.isTimeout()) {
                    appendLog("[ACTIVATE] CMD:2 超时");
                    // 重试一次
                    appendLog("[ACTIVATE] 重试 CMD:2...");
                    H8TcpManager.H8CommandResult r2r = mTcpManager.sendCommandAndWait(
                            2, H8Constants.Command.VID_ENC_PREVIEW_ON, "", 3000
                    );
                    appendLog("[ACTIVATE] CMD:2 重试响应: " + r2r);
                } else if (r2.isSuccess()) {
                    appendLog("[ACTIVATE] ★ CMD:2 成功! 视频编码已激活");
                } else {
                    appendLog("[ACTIVATE] CMD:2 返回 RESULT=" + r2.result + " PARAM=" + r2.param);
                }

                try { Thread.sleep(300); } catch (InterruptedException ignored) {}

                // Step 3: CMD:116 — TCP 连接已建立通知
                appendLog("[ACTIVATE] 发送 CMD:116 (TCP连接通知)...");
                H8TcpManager.H8CommandResult r116 = mTcpManager.sendCommandAndWait(
                        116, H8Constants.Command.CMD_TCP_CONNECTED, "", 2000
                );
                appendLog("[ACTIVATE] CMD:116 响应: " + r116);

                // 等待无人机准备就绪
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

                // 标记视频已激活
                mVideoActivated = true;
                appendLog("[ACTIVATE] ★ 视频激活序列完成，准备启动 UDP 接收");

                // 启动 UDP 视频接收
                startUdpVideo();
            }
        }.start();
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

    @Override
    public void onUdpVideoData(byte[] data, int length) {
        // 收到 UDP 视频包 -> RTP 去包化 -> 提取 NAL 单元 -> 送入解码器
        if (length <= 0 || !mStreaming) return;

        try {
            // RTP 去包化，提取 H.264 NAL 单元
            byte[] nalUnit = mRtpDepacketizer.parseRtpPacket(data, length);
            if (nalUnit != null && nalUnit.length > 0) {
                // 送入解码器
                mVideoDecoder.offerData(nalUnit);
            }
        } catch (Exception e) {
            Log.w(TAG, "处理 UDP 数据异常: " + e.getMessage());
        }
    }

    @Override
    public void onFirstFrame() {
        Log.d(TAG, "收到第一帧 UDP 视频数据");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendLog("✓ 收到第一帧 UDP 视频数据!");
                updateStatus("视频流接收中 - OK");
            }
        });
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
     * 追加日志到日志区域
     */
    private void appendLog(final String text) {
        final String timestamp = mLogTimeFormat.format(new Date());
        final String logLine = "[" + timestamp + "] " + text + "\n";

        Log.d(TAG, "LOG: " + text);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mTvLog != null) {
                    mTvLog.append(logLine);
                    // 限制日志长度 (最多 500 行)
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
}
