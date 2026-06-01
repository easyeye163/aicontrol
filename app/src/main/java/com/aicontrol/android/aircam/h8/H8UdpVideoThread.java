package com.aicontrol.android.aircam.h8;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * H8 无人机 UDP 视频接收线程
 *
 * 基于 HFun APK 逆向分析中的 p2.a (UdpThread) 重构。
 *
 * 工作流程:
 * 1. 绑定本地 UDP 端口 1563
 * 2. 连接 (connect) 到无人机 192.168.100.1:1563
 * 3. 发送 3 字节握手魔数: D8 C0 D9
 * 4. 开始接收 RTP/H.264 视频数据包
 * 5. 每收到一个包通过回调通知上层处理
 *
 * Socket 配置:
 * - SO_REUSEADDR: 允许地址复用
 * - SO_TIMEOUT: 3000ms 超时 (用于检测断线)
 * - SO_BROADCAST: 允许广播
 *
 * 异常处理:
 * - 收到异常后关闭 socket，等待 40ms 后重试
 *
 * 使用方式:
 * <pre>
 *   H8UdpVideoThread udpThread = new H8UdpVideoThread(callback);
 *   udpThread.start();
 *   // ...
 *   udpThread.stop();
 * </pre>
 */
public class H8UdpVideoThread {

    private static final String TAG = "H8Udp";

    /** UDP 视频数据回调接口 */
    public interface H8UdpVideoCallback {

        /**
         * 收到 UDP 视频数据包
         *
         * @param data   数据包字节内容
         * @param length 有效数据长度
         */
        void onUdpVideoData(byte[] data, int length);

        /**
         * 收到第一帧数据通知
         * 可用于启动解码器或更新 UI 状态
         */
        void onFirstFrame();
    }

    /** 接收回调 */
    private H8UdpVideoCallback mCallback;

    /** UDP 套接字 */
    private DatagramSocket mSocket;

    /** 接收线程 */
    private volatile Thread mRecvThread;

    /** 是否停止标志 */
    private volatile boolean mRunning = false;

    /** 是否已收到过第一帧 */
    private volatile boolean mFirstFrameReceived = false;

    /**
     * 构造 UDP 视频接收线程
     *
     * @param callback 视频数据回调
     */
    public H8UdpVideoThread(H8UdpVideoCallback callback) {
        this.mCallback = callback;
    }

    // ======================== 启动/停止 ========================

    /**
     * 启动 UDP 视频接收线程
     * 绑定端口、发送握手、开始接收数据
     */
    public void start() {
        if (mRunning) {
            Log.w(TAG, "UDP 接收线程已在运行");
            return;
        }

        mRunning = true;
        mFirstFrameReceived = false;

        mRecvThread = new Thread("H8UdpRecv") {
            @Override
            public void run() {
                receiveLoop();
            }
        };
        mRecvThread.setDaemon(true);
        mRecvThread.start();

        Log.d(TAG, "UDP 视频接收线程已启动");
    }

    /**
     * 停止 UDP 视频接收线程
     * 关闭套接字、中断线程
     */
    public void stop() {
        mRunning = false;

        if (mRecvThread != null) {
            mRecvThread.interrupt();
            mRecvThread = null;
        }

        closeSocket();

        Log.d(TAG, "UDP 视频接收线程已停止");
    }

    /**
     * @return 接收线程是否正在运行
     */
    public boolean isRunning() {
        return mRunning;
    }

    // ======================== Socket 管理 ========================

    /**
     * 关闭 UDP 套接字
     */
    private void closeSocket() {
        if (mSocket != null && !mSocket.isClosed()) {
            try {
                mSocket.close();
            } catch (Exception ignored) {
            }
            mSocket = null;
        }
    }

    // ======================== 接收循环 ========================

    /**
     * UDP 接收主循环
     * 创建 socket -> 握手 -> 接收数据
     */
    private void receiveLoop() {
        while (mRunning) {
            try {
                // 创建并配置 UDP 套接字
                createSocket();

                // 发送握手魔数
                sendHandshake();

                Log.d(TAG, "UDP 握手完成，开始接收视频数据");

                // 进入接收循环
                doReceive();

            } catch (SocketException e) {
                if (!mRunning) {
                    // 主动停止，正常退出
                    break;
                }
                Log.w(TAG, "Socket 异常，准备重连: " + e.getMessage());
            } catch (Exception e) {
                if (!mRunning) {
                    break;
                }
                Log.w(TAG, "UDP 接收异常: " + e.getMessage());
            } finally {
                closeSocket();
            }

            // 等待后重试
            if (mRunning) {
                try {
                    Thread.sleep(H8Constants.HANDSHAKE_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 创建 UDP 套接字并配置参数
     */
    private void createSocket() throws SocketException {
        mSocket = new DatagramSocket(null);
        mSocket.setReuseAddress(true);
        mSocket.bind(new InetSocketAddress(H8Constants.UDP_VIDEO_PORT));
        mSocket.setSoTimeout(H8Constants.CONNECT_TIMEOUT_MS);
        mSocket.setBroadcast(true);
        mSocket.connect(new InetSocketAddress(H8Constants.DRONE_IP, H8Constants.UDP_VIDEO_PORT));

        Log.d(TAG, "UDP socket 已绑定端口 " + H8Constants.UDP_VIDEO_PORT
                + "，连接到 " + H8Constants.DRONE_IP + ":" + H8Constants.UDP_VIDEO_PORT);
    }

    /**
     * 发送 3 字节握手魔数到无人机
     */
    private void sendHandshake() throws Exception {
        DatagramPacket handshakePacket = new DatagramPacket(
                H8Constants.HANDSHAKE_MAGIC,
                H8Constants.HANDSHAKE_MAGIC.length
        );
        mSocket.send(handshakePacket);
        Log.d(TAG, "握手魔数已发送: D8 C0 D9");
    }

    /**
     * 接收数据包循环
     */
    private void doReceive() {
        byte[] buffer = new byte[H8Constants.RECEIVE_BUFFER_SIZE];

        while (mRunning) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                mSocket.receive(packet);

                int length = packet.getLength();
                if (length <= 0) continue;

                // 通知第一帧
                if (!mFirstFrameReceived) {
                    mFirstFrameReceived = true;
                    Log.d(TAG, "收到第一帧 UDP 视频数据，长度: " + length);
                    if (mCallback != null) {
                        mCallback.onFirstFrame();
                    }
                }

                // 回调通知视频数据
                if (mCallback != null) {
                    mCallback.onUdpVideoData(buffer, length);
                }

            } catch (SocketTimeoutException e) {
                // 超时是正常的，继续接收
                Log.d(TAG, "UDP 接收超时 (3s)，继续等待...");
            } catch (SocketException e) {
                if (!mRunning) {
                    break;
                }
                Log.w(TAG, "Socket 异常: " + e.getMessage());
            } catch (Exception e) {
                if (!mRunning) {
                    break;
                }
                Log.w(TAG, "接收数据包异常: " + e.getMessage());
            }
        }
    }
}
