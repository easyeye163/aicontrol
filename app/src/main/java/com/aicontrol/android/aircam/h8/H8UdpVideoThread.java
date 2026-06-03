package com.aicontrol.android.aircam.h8;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * H8 无人机 UDP 视频接收线程 v0.0.103
 *
 * v0.0.103 增强:
 * - 详细的连接诊断日志 (socket创建/绑定/握手/超时)
 * - 超时从 3s 改为 2s (更快检测无数据)
 * - 超时计数日志 (记录连续超时次数)
 * - 通过 callback 通知上层日志消息
 *
 * 工作流程:
 * 1. 绑定本地 UDP 端口 1563
 * 2. 连接 (connect) 到无人机 192.168.100.1:1563
 * 3. 发送 3 字节握手魔数: D8 C0 D9
 * 4. 开始接收 RTP/H.264 视频数据包
 */
public class H8UdpVideoThread {

    private static final String TAG = "H8Udp";

    /** UDP 视频数据回调接口 */
    public interface H8UdpVideoCallback {

        void onUdpVideoData(byte[] data, int length);

        void onFirstFrame();

        /** v0.0.103: UDP 诊断日志回调 */
        void onUdpLog(String message);
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

    public H8UdpVideoThread(H8UdpVideoCallback callback) {
        this.mCallback = callback;
    }

    // ======================== 启动/停止 ========================

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

    public void stop() {
        mRunning = false;

        if (mRecvThread != null) {
            mRecvThread.interrupt();
            mRecvThread = null;
        }

        closeSocket();

        Log.d(TAG, "UDP 视频接收线程已停止");
    }

    public boolean isRunning() {
        return mRunning;
    }

    // ======================== Socket 管理 ========================

    private void closeSocket() {
        if (mSocket != null && !mSocket.isClosed()) {
            try {
                mSocket.close();
            } catch (Exception ignored) {
            }
            mSocket = null;
        }
    }

    /** 发送诊断日志到回调 */
    private void logDiag(String msg) {
        Log.d(TAG, msg);
        if (mCallback != null) {
            mCallback.onUdpLog(msg);
        }
    }

    // ======================== 接收循环 ========================

    private void receiveLoop() {
        while (mRunning) {
            try {
                createSocket();
                sendHandshake();

                logDiag("[UDP] 握手已发送，等待视频数据...");

                doReceive();

            } catch (SocketException e) {
                if (!mRunning) break;
                logDiag("[UDP] Socket 异常: " + e.getMessage());
            } catch (Exception e) {
                if (!mRunning) break;
                logDiag("[UDP] 接收异常: " + e.getMessage());
            } finally {
                closeSocket();
            }

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
        logDiag("[UDP] 创建 socket, 绑定端口 " + H8Constants.UDP_VIDEO_PORT + "...");

        mSocket = new DatagramSocket(null);
        mSocket.setReuseAddress(true);
        mSocket.bind(new InetSocketAddress(H8Constants.UDP_VIDEO_PORT));
        // v0.0.103: 超时从 3s 改为 2s
        mSocket.setSoTimeout(2000);
        mSocket.setBroadcast(true);
        mSocket.connect(new InetSocketAddress(H8Constants.DRONE_IP, H8Constants.UDP_VIDEO_PORT));

        int localPort = mSocket.getLocalPort();
        logDiag("[UDP] socket 已就绪: local=" + localPort + " → " + H8Constants.DRONE_IP + ":" + H8Constants.UDP_VIDEO_PORT);
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
        logDiag("[UDP] 握手魔数已发送: D8 C0 D9 (3 bytes)");
    }

    /**
     * 接收数据包循环
     */
    private void doReceive() {
        byte[] buffer = new byte[H8Constants.RECEIVE_BUFFER_SIZE];
        int timeoutCount = 0;
        int totalPackets = 0;

        while (mRunning) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                mSocket.receive(packet);

                int length = packet.getLength();
                if (length <= 0) continue;

                timeoutCount = 0;
                totalPackets++;

                // 首包日志
                if (totalPackets == 1) {
                    logDiag("[UDP] ✓ 收到首个数据包! 大小=" + length + "B, 来自=" + packet.getAddress().getHostAddress() + ":" + packet.getPort());
                }

                // 通知第一帧
                if (!mFirstFrameReceived) {
                    mFirstFrameReceived = true;
                    Log.d(TAG, "收到第一帧 UDP 视频数据，长度: " + length);
                    if (mCallback != null) {
                        mCallback.onFirstFrame();
                    }
                }

                // 每500包输出一次诊断
                if (totalPackets % 500 == 0) {
                    logDiag("[UDP] 已接收 " + totalPackets + " 包 (最新 " + length + "B)");
                }

                // 回调通知视频数据
                if (mCallback != null) {
                    mCallback.onUdpVideoData(buffer, length);
                }

            } catch (SocketTimeoutException e) {
                timeoutCount++;
                // v0.0.103: 每3次超时输出一次日志 (避免刷屏)
                if (timeoutCount <= 3 || timeoutCount % 3 == 0) {
                    logDiag("[UDP] 接收超时 (" + timeoutCount + " 次, 已收 " + totalPackets + " 包)");
                }
                // 连续10次超时 (20s无数据) 日志警告
                if (timeoutCount == 10) {
                    logDiag("[UDP] ⚠ 连续20秒无数据, 可能UDP未建立");
                }
            } catch (SocketException e) {
                if (!mRunning) break;
                logDiag("[UDP] Socket 异常: " + e.getMessage());
            } catch (Exception e) {
                if (!mRunning) break;
                logDiag("[UDP] 接收异常: " + e.getMessage());
            }
        }

        logDiag("[UDP] 接收循环结束, 共 " + totalPackets + " 包");
    }
}
