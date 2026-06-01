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

/**
 * H8 无人机 TCP 控制通道管理器
 *
 * 基于 HFun APK 逆向分析中的 o2.a (TcpManager) 重构。
 *
 * 功能:
 * - 连接 H8 无人机 TCP 端口 4646，超时 3 秒
 * - 以换行符分隔的 JSON 字符串收发命令
 * - 接收 JSON 格式响应: {"CMD":int, "RESULT":int, "PARAM":"string"}
 * - CMD=0 响应包含固件字符串 (如 "H8-720P-6-1-1-2-00020003-2-1-1-4")
 * - 断线后每 200ms 自动重连
 * - 观察者模式分发连接事件和命令响应
 *
 * 线程模型:
 * - connectAsync(): 在后台线程中建立连接
 * - 读取循环在独立线程中运行
 * - 观察者回调在读取线程中同步执行 (非主线程)
 *
 * 使用方式:
 * <pre>
 *   H8TcpManager tcpManager = new H8TcpManager();
 *   tcpManager.registerObserver(this);
 *   tcpManager.connectAsync();
 *   // ...
 *   tcpManager.sendCommand(H8Constants.Command.SYS_PARAM_GET);
 *   // ...
 *   tcpManager.disconnect();
 * </pre>
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

            notifyCommand(cmd, result, param);
            return true;

        } catch (JSONException e) {
            // 不是有效 JSON，返回 false 让调用者作为二进制处理
            Log.d(TAG, "非JSON数据: " + jsonLine.length() + "字符");
            return false;
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
}
