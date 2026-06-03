package com.aicontrol.android.aircam.h8;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.PortUnreachableException;

/**
 * H8 无人机 UDP 视频接收线程 v0.0.104
 *
 * v0.0.104 核心修复:
 * - 只 bind 不 connect: 无人机主动推送视频到 APP 的 1563 端口
 * - 从任意来源接收 (无人机可能从不同端口发送)
 * - ICMP Port Unreachable 不再导致 socket 关闭
 * - 记录首个数据包的来源地址和端口
 * - 握手用 sendTo 而非通过 connected socket 发送
 *
 * 协议模型 (修正):
 * - APP 绑定本地 UDP 1563 端口
 * - CMD:94 告诉无人机 "APP 在 1563 端口接收"
 * - 无人机开始发送 RTP/H.264 到 APP:1563
 * - APP 从任意源接收 (不限定 drone 端口)
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

    private H8UdpVideoCallback mCallback;
    private DatagramSocket mSocket;
    private volatile Thread mRecvThread;
    private volatile boolean mRunning = false;
    private volatile boolean mFirstFrameReceived = false;

    /** 首个数据包来源地址 (无人机可能从非1563端口发送) */
    private volatile String mSourceAddress = null;
    private volatile int mSourcePort = 0;

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
        mSourceAddress = null;
        mSourcePort = 0;

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

    private void closeSocket() {
        if (mSocket != null && !mSocket.isClosed()) {
            try { mSocket.close(); } catch (Exception ignored) {}
            mSocket = null;
        }
    }

    private void logDiag(String msg) {
        Log.d(TAG, msg);
        if (mCallback != null) mCallback.onUdpLog(msg);
    }

    // ======================== 接收循环 ========================

    private void receiveLoop() {
        while (mRunning) {
            try {
                createSocket();
                sendHandshake();
                logDiag("[UDP] 握手已发送，等待视频数据 (从任意源接收)...");
                doReceive();
            } catch (SocketException e) {
                if (!mRunning) break;
                logDiag("[UDP] Socket 异常: " + e.getMessage());
            } catch (Exception e) {
                if (!mRunning) break;
                logDiag("[UDP] 异常: " + e.getMessage());
            } finally {
                closeSocket();
            }

            if (mRunning) {
                try { Thread.sleep(H8Constants.HANDSHAKE_RETRY_DELAY_MS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    /**
     * v0.0.104: 只 bind 本地端口，不 connect 到无人机
     *
     * 之前: socket.bind(1563) + socket.connect(drone:1563)
     *   → 导致 ICMP Port Unreachable (无人机不在 1563 监听)
     *
     * 现在: socket.bind(0.0.0.0:1563)，从任意来源接收
     *   → 无人机主动推送到 APP:1563，APP 接收即可
     */
    private void createSocket() throws SocketException {
        logDiag("[UDP] 创建 socket, 绑定本地端口 " + H8Constants.UDP_VIDEO_PORT + " (不连接远端)...");

        mSocket = new DatagramSocket(null);
        mSocket.setReuseAddress(true);
        // v0.0.104: 绑定到 0.0.0.0:1563，接收来自任意地址的数据包
        mSocket.bind(new InetSocketAddress("0.0.0.0", H8Constants.UDP_VIDEO_PORT));
        mSocket.setSoTimeout(2000);
        mSocket.setBroadcast(true);
        // v0.0.104: 不调用 socket.connect()！

        logDiag("[UDP] socket 已绑定: local=0.0.0.0:" + mSocket.getLocalPort()
                + ", 等待无人机推送到此端口");
    }

    /**
     * v0.0.104: 用 sendTo 发送握手到无人机 (不依赖 connect)
     */
    private void sendHandshake() throws Exception {
        DatagramPacket handshakePacket = new DatagramPacket(
                H8Constants.HANDSHAKE_MAGIC,
                H8Constants.HANDSHAKE_MAGIC.length,
                new InetSocketAddress(H8Constants.DRONE_IP, H8Constants.UDP_VIDEO_PORT)
        );
        mSocket.send(handshakePacket);
        logDiag("[UDP] 握手魔数已发送到 " + H8Constants.DRONE_IP + ":"
                + H8Constants.UDP_VIDEO_PORT + " → D8 C0 D9");
    }

    /**
     * 接收数据包循环
     * v0.0.104: 接收来自任意源的数据包，记录实际来源
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

                // 首包: 记录实际来源
                if (totalPackets == 1) {
                    mSourceAddress = packet.getAddress().getHostAddress();
                    mSourcePort = packet.getPort();
                    logDiag("[UDP] ✓ 首包到达! 来源=" + mSourceAddress + ":" + mSourcePort
                            + ", 大小=" + length + "B");
                }

                // 每500包输出统计
                if (totalPackets % 500 == 0) {
                    logDiag("[UDP] 已接收 " + totalPackets + " 包 (来源="
                            + mSourceAddress + ":" + mSourcePort + ", 最新 " + length + "B)");
                }

                // 通知第一帧
                if (!mFirstFrameReceived) {
                    mFirstFrameReceived = true;
                    Log.d(TAG, "收到第一帧 UDP 视频数据");
                    if (mCallback != null) mCallback.onFirstFrame();
                }

                // 回调
                if (mCallback != null) mCallback.onUdpVideoData(buffer, length);

            } catch (SocketTimeoutException e) {
                timeoutCount++;
                if (timeoutCount <= 3 || timeoutCount % 3 == 0) {
                    logDiag("[UDP] 接收超时 (" + timeoutCount + "次, 已收 " + totalPackets + " 包)");
                }
                if (timeoutCount == 10) {
                    logDiag("[UDP] ⚠ 连续20秒无数据");
                }
            } catch (PortUnreachableException e) {
                // v0.0.104: ICMP Port Unreachable 不再致命，只记录
                timeoutCount++;
                if (timeoutCount <= 2) {
                    logDiag("[UDP] ICMP Port Unreachable (无人机未在此端口监听, 继续等待...)");
                }
            } catch (SocketException e) {
                if (!mRunning) break;
                String msg = e.getMessage();
                if (msg != null && msg.contains("Port unreachable")) {
                    timeoutCount++;
                    if (timeoutCount <= 2) {
                        logDiag("[UDP] ICMP Port Unreachable (继续等待...)");
                    }
                } else {
                    logDiag("[UDP] Socket 异常: " + msg);
                    break;
                }
            } catch (Exception e) {
                if (!mRunning) break;
                logDiag("[UDP] 异常: " + e.getMessage());
            }
        }

        logDiag("[UDP] 接收循环结束, 共 " + totalPackets + " 包"
                + (mSourceAddress != null ? " (来源=" + mSourceAddress + ":" + mSourcePort + ")" : ""));
    }

    /**
     * @return 首个数据包的来源地址 (无人机实际发送端口)
     */
    public String getSourceInfo() {
        return mSourceAddress != null ? mSourceAddress + ":" + mSourcePort : "无";
    }
}
