package com.aicontrol.android.aircam.h8;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * H8 无人机 TCP 控制通道管理器 v0.0.99
 *
 * 基于 HFun APK 逆向分析中的 o2.a (TcpManager) 重构。
 *
 * v0.0.99 变更:
 * - 新增 sendCommandAndWait() 同步阻塞方法，用于命令序列化发送
 * - banner 自动解析（嵌套 JSON PARAM 字段）
 * - 增强二进制数据检测和日志
 *
 * 功能:
 * - 连接 H8 无人机 TCP 端口 4646，超时 3 秒
 * - 以换行符分隔的 JSON 字符串收发命令
 * - 接收 JSON 格式响应: {"CMD":int, "RESULT":int, "PARAM":"string"}
 * - CMD=0 响应包含固件字符串 (如 "H8-720P-6-1-1-2-00020003-2-1-1-4")
 * - 断线后每 200ms 自动重连
 * - 观察者模式分发连接事件和命令响应
 * - 同步 sendCommandAndWait 用于初始化命令序列 (CMD:94 → CMD:2)
 *
 * 线程模型:
 * - connectAsync(): 在后台线程中建立连接
 * - 读取循环在独立线程中运行
 * - 观察者回调在读取线程中同步执行 (非主线程)
 * - sendCommandAndWait(): 在调用线程中阻塞等待响应
 */
public class H8TcpManager {

    private static final String TAG = "H8Tcp";

    /** TCP 套接字 */
    private Socket mSocket;

    /** 原始输出流 (用于二进制发送) */
    private java.io.OutputStream mRawOutputStream;

    /** 输出流写入器 (发送 JSON 命令) - BufferedWriter like original H8 */
    private BufferedWriter mWriter;

    /** TCP 读取线程 */
    private Thread mReadThread;

    /** 重连线程 */
    private Thread mReconnectThread;

    /** 是否已请求断开 (防止重连) */
    private volatile boolean mShouldStop = false;

    /** 当前连接状态 */
    private volatile boolean mIsConnected = false;

    /** 是否已收到固件 banner (CMD:0 响应) */
    private volatile boolean mBannerReceived = false;

    /** 观察者列表 (线程安全) */
    private final CopyOnWriteArrayList<H8TcpObserver> mObservers = new CopyOnWriteArrayList<>();

    // ======================== 同步命令等待机制 ========================

    /** 同步等待的命令码 (-1 表示空闲) */
    private final AtomicReference<Integer> mWaitingForCmd = new AtomicReference<>(-1);

    /** 同步等待的 CountDownLatch */
    private volatile CountDownLatch mWaitLatch = null;

    /** 同步等待的结果码 */
    private volatile int mWaitResult = -999;

    /** 同步等待的 PARAM 值 */
    private volatile String mWaitParam = "";

    // ======================== 连接管理 ========================

    /**
     * 异步连接到 H8 无人机 TCP 端口
     * 连接成功后自动启动读取线程
     */
    public void connectAsync() {
        mShouldStop = false;
        Thread connectThread = new Thread("H8TcpConnect") {
            @Override
            public void run() {
                doConnect();
            }
        };
        connectThread.setDaemon(true);
        connectThread.start();
    }

    /**
     * 执行 TCP 连接 (阻塞)
     * 连接成功后启动读取线程
     */
    private void doConnect() {
        if (mShouldStop) return;

        disconnectInternal();

        try {
            Log.d(TAG, "正在连接 " + H8Constants.DRONE_IP + ":" + H8Constants.TCP_PORT + " ...");

            mSocket = new Socket();
            mSocket.connect(
                    new InetSocketAddress(H8Constants.DRONE_IP, H8Constants.TCP_PORT),
                    H8Constants.CONNECT_TIMEOUT_MS
            );

            // 保存原始输出流，与原版 H8 TcpManager 一致
            mRawOutputStream = mSocket.getOutputStream();
            // BufferedWriter 包装 OutputStreamWriter，用于发送换行分隔的 JSON
            mWriter = new BufferedWriter(new OutputStreamWriter(mRawOutputStream, "UTF-8"));
            mIsConnected = true;
            mBannerReceived = false;

            Log.d(TAG, "TCP 连接成功");
            notifyConnected();

            // 启动读取线程
            startReadThread();

        } catch (SocketTimeoutException e) {
            Log.w(TAG, "TCP 连接超时 (" + H8Constants.CONNECT_TIMEOUT_MS + "ms)");
            scheduleReconnect();
        } catch (Exception e) {
            Log.w(TAG, "TCP 连接失败: " + e.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * 主动断开连接
     * 停止读取线程、关闭套接字，不再自动重连
     */
    public void disconnect() {
        mShouldStop = true;
        disconnectInternal();
        notifyDisconnected();
    }

    /**
     * 内部断开: 关闭套接字和线程，不触发重连
     */
    private void disconnectInternal() {
        mIsConnected = false;

        // 中断读取线程
        if (mReadThread != null) {
            mReadThread.interrupt();
            mReadThread = null;
        }

        // 中断重连线程
        if (mReconnectThread != null) {
            mReconnectThread.interrupt();
            mReconnectThread = null;
        }

        // 释放同步等待
        releaseSyncWait();

        // 关闭写入器
        if (mWriter != null) {
            try {
                mWriter.close();
            } catch (Exception ignored) {
            }
            mWriter = null;
        }
        mRawOutputStream = null;

        // 关闭套接字
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (Exception ignored) {
            }
            mSocket = null;
        }
    }

    /**
     * 安排自动重连 (延迟 200ms)
     */
    private void scheduleReconnect() {
        if (mShouldStop) return;

        if (mReconnectThread != null && mReconnectThread.isAlive()) {
            return; // 已有重连线程在运行
        }

        mReconnectThread = new Thread("H8TcpReconnect") {
            @Override
            public void run() {
                try {
                    Thread.sleep(H8Constants.RECONNECT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!mShouldStop) {
                    doConnect();
                }
            }
        };
        mReconnectThread.setDaemon(true);
        mReconnectThread.start();
    }

    // ======================== 读取线程 ========================

    /**
     * 启动 TCP 读取线程
     * 循环读取以换行符分隔的 JSON 字符串
     */
    private void startReadThread() {
        mReadThread = new Thread("H8TcpRead") {
            @Override
            public void run() {
                readLoop();
            }
        };
        mReadThread.setDaemon(true);
        mReadThread.start();
    }

    /**
     * 读取循环 (在后台线程运行)
     * 与原版 H8 TcpManager 一致：使用 DataInputStream.read(byte[]) 读取原始字节，
     * 用 ISO-8859-1 编码转为字符串，尝试 JSON 解析，失败则作为二进制数据分发。
     */
    private void readLoop() {
        try {
            DataInputStream dis = new DataInputStream(mSocket.getInputStream());
            byte[] buffer = new byte[1024];

            while (!Thread.currentThread().isInterrupted() && mIsConnected) {
                int bytesRead = dis.read(buffer);
                if (bytesRead == -1) {
                    Log.d(TAG, "TCP 读取: 连接已关闭 (EOF)");
                    break;
                }
                if (bytesRead <= 0) continue;

                // 用 ISO-8859-1 编码（字节保持映射），与原版 H8 一致
                String data = new String(buffer, 0, bytesRead, "ISO-8859-1").trim();

                // 输出 hex 调试日志
                if (data.length() < 200) {
                    StringBuilder hex = new StringBuilder();
                    for (int i = 0; i < Math.min(bytesRead, 32); i++) {
                        hex.append(String.format("%02X ", buffer[i] & 0xFF));
                    }
                    Log.d(TAG, "TCP recv " + bytesRead + "B hex: " + hex);
                }

                // 尝试 JSON 解析
                boolean parsed = parseAndNotify(data);
                if (!parsed) {
                    // JSON 解析失败，作为二进制数据分发
                    Log.d(TAG, "TCP 二进制数据: " + bytesRead + " 字节 (非JSON)");
                    byte[] binaryData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, binaryData, 0, bytesRead);

                    // 检查二进制等待
                    if (mBinaryWaitLatch != null) {
                        mBinaryWaitData = binaryData;
                        mBinaryWaitLatch.countDown();
                    }

                    notifyData(binaryData);
                }
            }
        } catch (IOException e) {
            if (!mShouldStop) {
                Log.w(TAG, "TCP 读取异常: " + e.getMessage());
            }
        } finally {
            mIsConnected = false;
            notifyDisconnected();

            if (!mShouldStop) {
                scheduleReconnect();
            }
        }
    }

    // ======================== JSON 解析 ========================

    /**
     * 解析 JSON 行并通知观察者
     * 与原版 H8 TcpManager.c.a(String) 一致
     * JSON 格式: {"CMD":int, "RESULT":int, "PARAM":"string"}
     *
     * v0.0.99: 增加对同步等待机制的支持
     *
     * @return true 如果成功解析为 JSON，false 如果不是有效 JSON
     */
    private boolean parseAndNotify(String jsonLine) {
        try {
            JSONObject json = new JSONObject(jsonLine);

            int cmd = json.optInt("CMD", -1);
            int result = json.optInt("RESULT", -1);
            String param = json.optString("PARAM", "");

            Log.d(TAG, "解析命令: CMD=" + cmd + " RESULT=" + result + " PARAM=" + param);

            // 标记 banner 已收到 (CMD=0)
            if (cmd == 0) {
                mBannerReceived = true;
            }

            // 检查是否有同步等待者
            checkSyncWait(cmd, result, param);

            // 异步通知观察者
            notifyCommand(cmd, result, param);
            return true;

        } catch (JSONException e) {
            // 不是有效 JSON，返回 false 让调用者作为二进制处理
            Log.d(TAG, "非JSON数据: " + jsonLine.length() + "字符");
            return false;
        }
    }

    // ======================== 同步命令等待 ========================

    /**
     * 发送命令并同步等待指定 CMD 的响应
     *
     * 用于初始化阶段的命令序列，例如:
     *   sendCommandAndWait(94, Command.CMD_BIND_APP_UDP, "1563", 3000)
     *   sendCommandAndWait(2, Command.VID_ENC_PREVIEW_ON, "", 3000)
     *
     * 注意: 此方法会阻塞调用线程。必须在后台线程中调用，不能在主线程调用。
     *
     * @param expectedCmd 期望收到的响应 CMD 码
     * @param cmd         要发送的命令枚举
     * @param param       命令参数
     * @param timeoutMs   超时时间（毫秒）
     * @return H8CommandResult 包含 result 和 param
     */
    public H8CommandResult sendCommandAndWait(int expectedCmd, H8Constants.Command cmd, String param, long timeoutMs) {
        return sendCommandAndWait(expectedCmd, cmd.getCode(), param, timeoutMs);
    }

    /**
     * 发送命令并同步等待指定 CMD 的响应
     *
     * @param expectedCmd 期望收到的响应 CMD 码
     * @param cmdCode     要发送的命令码
     * @param param       命令参数
     * @param timeoutMs   超时时间（毫秒）
     * @return H8CommandResult 包含 result 和 param
     */
    public H8CommandResult sendCommandAndWait(int expectedCmd, int cmdCode, String param, long timeoutMs) {
        // 设置等待状态
        if (!mWaitingForCmd.compareAndSet(-1, expectedCmd)) {
            Log.w(TAG, "已有同步等待在进行 (CMD=" + mWaitingForCmd.get() + ")，无法发送");
            return new H8CommandResult(-999, "SYNC_BUSY");
        }

        mWaitLatch = new CountDownLatch(1);
        mWaitResult = -999;
        mWaitParam = "";

        try {
            // 发送命令
            sendCommand(cmdCode, param);

            // 等待响应
            boolean waited = mWaitLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!waited) {
                Log.w(TAG, "sendCommandAndWait 超时 (CMD=" + expectedCmd + ", " + timeoutMs + "ms)");
                return new H8CommandResult(-1, "TIMEOUT");
            }

            return new H8CommandResult(mWaitResult, mWaitParam);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new H8CommandResult(-1, "INTERRUPTED");
        } finally {
            releaseSyncWait();
        }
    }

    /**
     * 检查同步等待状态
     * 如果当前等待的 CMD 匹配收到的 CMD，释放 latch
     */
    private void checkSyncWait(int cmd, int result, String param) {
        int waiting = mWaitingForCmd.get();
        if (waiting >= 0 && cmd == waiting && mWaitLatch != null) {
            mWaitResult = result;
            mWaitParam = param;
            mWaitLatch.countDown();
        }
    }

    /**
     * 释放同步等待状态
     */
    private void releaseSyncWait() {
        mWaitingForCmd.set(-1);
        if (mWaitLatch != null) {
            mWaitLatch.countDown(); // 确保不会死锁
            mWaitLatch = null;
        }
    }

    // ======================== 命令发送 ========================

    /**
     * 发送 JSON 命令到无人机
     * 格式与原版 H8 一致: {"CMD": <int>, "PARAM": <value>}\n
     * @param cmd     命令枚举
     * @param param   PARAM 值 (int 用 String 表示, 如 "0", "1")
     */
    public void sendCommand(final H8Constants.Command cmd, final String param) {
        sendCommand(cmd.getCode(), param);
    }

    /**
     * 发送 JSON 命令到无人机
     * 格式与原版 H8 JsonUtil 一致: {"CMD": <int>, "PARAM": <value>}\n
     * @param cmdCode 命令码数值
     * @param param   PARAM 值字符串 (如 "0", "1", "\"start\"", 或嵌套JSON字符串)
     */
    public void sendCommand(final int cmdCode, final String param) {
        if (!mIsConnected || mWriter == null) {
            Log.w(TAG, "发送失败: TCP 未连接");
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("CMD", cmdCode);
            json.put("PARAM", param != null ? param : "");

            // 与原版 H8 一致: 替换换行符 + 追加换行符
            String cmdStr = json.toString().replace("\n", " ") + "\n";
            Log.d(TAG, "TCP 发送: " + cmdStr.trim());

            synchronized (mWriter) {
                mWriter.write(cmdStr);
                mWriter.flush();
            }

            // 与原版 H8 一致: 发送后 50ms 延迟
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        } catch (Exception e) {
            Log.e(TAG, "发送命令失败: " + e.getMessage());
        }
    }

    /**
     * 发送原始字符串命令 (与原版 H8 TcpManager.m(String) 一致)
     * 替换换行符 + 追加换行符 + 50ms 延迟
     *
     * @param cmdStr 命令字符串
     */
    public void sendRawCommand(final String cmdStr) {
        if (!mIsConnected || mWriter == null) {
            Log.w(TAG, "发送失败: TCP 未连接");
            return;
        }

        try {
            String sendStr = cmdStr.replace("\n", " ") + "\n";
            Log.d(TAG, "TCP 发送(原始): " + sendStr.trim());

            synchronized (mWriter) {
                mWriter.write(sendStr);
                mWriter.flush();
            }

            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        } catch (Exception e) {
            Log.e(TAG, "发送原始命令失败: " + e.getMessage());
        }
    }

    /**
     * 发送原始二进制数据
     *
     * @param data 二进制数据
     */
    public void sendRawBytes(byte[] data) {
        sendRawBytes(data, 1000);
    }

    /**
     * 发送原始二进制数据并等待响应
     *
     * @param data 二进制数据
     * @param timeoutMs 等待响应超时(毫秒)
     */
    public void sendRawBytes(byte[] data, long timeoutMs) {
        if (!mIsConnected || mRawOutputStream == null) {
            Log.w(TAG, "发送失败: TCP 未连接");
            return;
        }

        try {
            mRawOutputStream.write(data);
            mRawOutputStream.flush();
            Log.d(TAG, "TCP 发送二进制: " + data.length + "B");

            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        } catch (Exception e) {
            Log.e(TAG, "发送二进制数据失败: " + e.getMessage());
        }
    }

    /**
     * 发送二进制数据并同步等待响应
     *
     * @param data 二进制数据
     * @param timeoutMs 超时时间
     * @return 收到的响应数据，超时返回 null
     */
    public byte[] sendRawBytesAndWait(byte[] data, long timeoutMs) {
        if (!mIsConnected || mRawOutputStream == null) {
            Log.w(TAG, "发送失败: TCP 未连接");
            return null;
        }

        mBinaryWaitLatch = new CountDownLatch(1);
        mBinaryWaitData = null;

        try {
            mRawOutputStream.write(data);
            mRawOutputStream.flush();
            Log.d(TAG, "TCP 发送二进制(等待): " + data.length + "B");

            StringBuilder hex = new StringBuilder();
            for (byte b : data) hex.append(String.format("%02X ", b & 0xFF));
            Log.d(TAG, "发送: " + hex.toString());

            boolean waited = mBinaryWaitLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!waited) {
                Log.w(TAG, "二进制等待超时 (" + timeoutMs + "ms)");
                return null;
            }
            return mBinaryWaitData;
        } catch (Exception e) {
            Log.e(TAG, "发送二进制等待失败: " + e.getMessage());
            return null;
        } finally {
            mBinaryWaitLatch = null;
        }
    }

    /** 二进制响应等待 */
    private volatile CountDownLatch mBinaryWaitLatch = null;
    private volatile byte[] mBinaryWaitData = null;

    // ======================== 观察者管理 ========================

    /**
     * 注册观察者
     */
    public void registerObserver(H8TcpObserver observer) {
        if (observer != null && !mObservers.contains(observer)) {
            mObservers.add(observer);
            Log.d(TAG, "注册观察者: " + observer.getClass().getSimpleName());
        }
    }

    /**
     * 注销观察者
     */
    public void unregisterObserver(H8TcpObserver observer) {
        if (observer != null) {
            mObservers.remove(observer);
            Log.d(TAG, "注销观察者: " + observer.getClass().getSimpleName());
        }
    }

    // ======================== 状态查询 ========================

    /**
     * @return 当前是否已连接
     */
    public boolean isConnected() {
        return mIsConnected;
    }

    /**
     * @return 是否已收到固件 banner (CMD:0 响应)
     */
    public boolean isBannerReceived() {
        return mBannerReceived;
    }

    // ======================== 通知方法 ========================

    private void notifyConnected() {
        for (H8TcpObserver observer : mObservers) {
            try {
                observer.onTcpConnected();
            } catch (Exception e) {
                Log.w(TAG, "观察者 onTcpConnected 异常: " + e.getMessage());
            }
        }
    }

    private void notifyDisconnected() {
        for (H8TcpObserver observer : mObservers) {
            try {
                observer.onTcpDisconnected();
            } catch (Exception e) {
                Log.w(TAG, "观察者 onTcpDisconnected 异常: " + e.getMessage());
            }
        }
    }

    private void notifyCommand(int cmd, int result, String param) {
        for (H8TcpObserver observer : mObservers) {
            try {
                observer.onTcpCommand(cmd, result, param);
            } catch (Exception e) {
                Log.w(TAG, "观察者 onTcpCommand 异常: " + e.getMessage());
            }
        }
    }

    private void notifyData(byte[] data) {
        for (H8TcpObserver observer : mObservers) {
            try {
                observer.onTcpData(data);
            } catch (Exception e) {
                Log.w(TAG, "观察者 onTcpData 异常: " + e.getMessage());
            }
        }
    }

    // ======================== 命令结果容器 ========================

    /**
     * 同步命令等待的返回结果
     */
    public static class H8CommandResult {
        /** 响应 RESULT 码 (0=成功) */
        public final int result;

        /** 响应 PARAM 字符串 */
        public final String param;

        public H8CommandResult(int result, String param) {
            this.result = result;
            this.param = param;
        }

        public boolean isSuccess() {
            return result == 0;
        }

        public boolean isTimeout() {
            return "TIMEOUT".equals(param);
        }

        @Override
        public String toString() {
            return "H8CommandResult{result=" + result + ", param='" + param + "'}";
        }
    }
}
