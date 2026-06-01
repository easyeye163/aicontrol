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
 * H8 无人机视频播放主界面
 *
 * 将 TCP 控制通道、UDP 视频接收、RTP 去包化、H.264 解码整合在一起的 Activity。
 * 基于 aircam 框架的 BaseActivity 和 SurfaceViews 构建。
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
 *
 * 工作流程:
 * 1. 点击"连接" -> 建立 TCP 连接 -> 查询固件信息 (CMD:0)
 * 2. 点击"开始" -> 启动 UDP 接收线程 -> 启动 RTP 解包器 -> 启动 H.264 解码器
 * 3. 解码后的 Bitmap 通过 SurfaceViews.SetBitmap() 渲染
 * 4. 点击"停止" -> 停止解码器 -> 停止 UDP 线程
 * 5. Activity 销毁 -> 断开 TCP -> 释放所有资源
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

        // 开始按钮
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

        // 初始状态
        mBtnStart.setEnabled(false);
        mBtnStop.setEnabled(false);
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
        updateStatus("未连接");
        mBtnConnect.setText("连接");
        mBtnStart.setEnabled(false);
        appendLog("TCP 已断开");
    }

    // ======================== 视频流管理 ========================

    /**
     * 开始接收视频流
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

        appendLog("启动视频流...");

        // 初始化 RTP 去包化器
        mRtpDepacketizer.reset();

        // 创建并启动解码器
        mVideoDecoder.setCallback(this);
        mVideoDecoder.start();

        // 创建并启动 UDP 视频接收线程
        mUdpVideoThread = new H8UdpVideoThread(this);
        mUdpVideoThread.start();

        mStreaming = true;
        mFrameCount = 0;

        mBtnStart.setEnabled(false);
        mBtnStop.setEnabled(true);
        updateStatus("视频流接收中");
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

        mStreaming = false;
        mFrameCount = 0;

        mBtnStart.setEnabled(mTcpConnected);
        mBtnStop.setEnabled(false);
        if (mTcpConnected) {
            updateStatus("已连接 - 待命");
        }

        appendLog("视频流已停止");
    }

    // ======================== H8TcpObserver 实现 ========================

    @Override
    public void onTcpConnected() {
        Log.d(TAG, "TCP 已连接");
        mTcpConnected = true;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendLog("✓ TCP 已连接");
                updateStatus("已连接");
                mBtnConnect.setText("断开");
                mBtnStart.setEnabled(true);

                // 连接成功后查询固件信息
                mTcpManager.sendCommand(H8Constants.Command.SYS_PARAM_GET);
                appendLog("→ 发送 CMD:0 查询固件信息");
            }
        });
    }

    @Override
    public void onTcpDisconnected() {
        Log.d(TAG, "TCP 已断开");
        mTcpConnected = false;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendLog("✗ TCP 已断开");
                updateStatus("连接断开");
                mBtnConnect.setText("连接");
                mBtnStart.setEnabled(false);
                mBtnStop.setEnabled(false);

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

                // 处理固件信息 (CMD=0)
                if (cmd == H8Constants.Command.SYS_PARAM_GET.getCode() && result == 0 && !param.isEmpty()) {
                    mFirmwareInfo = param;
                    appendLog("固件: " + param);

                    // 解析固件字符串
                    parseFirmwareInfo(param);
                }
            }
        });
    }

    @Override
    public void onTcpData(byte[] data) {
        // 原始数据回调 (调试用)
        Log.d(TAG, "TCP 原始数据: " + data.length + " 字节");
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
                appendLog("✓ 收到第一帧 UDP 视频数据");
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
     * 格式: "H8-720P-6-1-1-2-00020003-2-1-1-4"
     *
     * @param firmwareStr 固件字符串
     */
    private void parseFirmwareInfo(String firmwareStr) {
        try {
            String[] parts = firmwareStr.split("-");
            if (parts.length < 2) return;

            String platform = parts[0]; // "H8"
            String resolution = parts[1]; // "720P"

            appendLog("平台: " + platform + ", 分辨率: " + resolution);

            // 根据分辨率设置解码器参数
            H8Constants.Resolution res = parseResolution(resolution);
            if (res != null) {
                appendLog("分辨率枚举: " + res.name());
            }
        } catch (Exception e) {
            Log.w(TAG, "解析固件信息失败: " + e.getMessage());
        }
    }

    /**
     * 解析分辨率字符串
     */
    private H8Constants.Resolution parseResolution(String resStr) {
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
