package com.aicontrol.android.aircam.h8;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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

    /** 输出流写入器 (发送 JSON 命令) */
    private OutputStreamWriter mWriter;

    /** TCP 读取线程 */
    private Thread mReadThread;

    /** 重连线程 */
    private Thread mReconnectThread;

    /** 是否已请求断开 (防止重连) */
    private volatile boolean mShouldStop = false;

    /** 当前连接状态 */
    private volatile boolean mIsConnected = false;

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

            mWriter = new OutputStreamWriter(mSocket.getOutputStream(), "UTF-8");
            mIsConnected = true;

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
     * 每行是一个 JSON 响应对象
     */
    private void readLoop() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(mSocket.getInputStream(), "UTF-8")
            );

            String line;
            while (!Thread.currentThread().isInterrupted() && mIsConnected) {
                line = reader.readLine();
                if (line == null) {
                    Log.d(TAG, "TCP 读取: 连接已关闭 (EOF)");
                    break;
                }
                if (line.isEmpty()) continue;

                Log.d(TAG, "TCP 收到: " + line);
                parseAndNotify(line);
            }
        } catch (Exception e) {
            if (!mShouldStop) {
                Log.w(TAG, "TCP 读取异常: " + e.getMessage());
            }
        } finally {
            mIsConnected = false;
            notifyDisconnected();

            // 如果不是主动断开，安排重连
            if (!mShouldStop) {
                scheduleReconnect();
            }
        }
    }

    // ======================== JSON 解析 ========================

    /**
     * 解析 JSON 行并通知观察者
     *
     * JSON 格式: {"CMD":int, "RESULT":int, "PARAM":"string"}
     */
    private void parseAndNotify(String jsonLine) {
        try {
            JSONObject json = new JSONObject(jsonLine);

            int cmd = json.optInt("CMD", -1);
            int result = json.optInt("RESULT", -1);
            String param = json.optString("PARAM", "");

            Log.d(TAG, "解析命令: CMD=" + cmd + " RESULT=" + result + " PARAM=" + param);

            notifyCommand(cmd, result, param);

            // 也通知原始数据
            notifyData(jsonLine.getBytes("UTF-8"));

        } catch (JSONException e) {
            Log.w(TAG, "JSON 解析失败: " + jsonLine + " | " + e.getMessage());
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "编码异常: " + e.getMessage());
        }
    }

    // ======================== 命令发送 ========================

    /**
     * 发送 JSON 命令到无人机
     *
     * @param cmd     命令码
     * @param params  附加参数键值对 (可为 null)
     */
    public void sendCommand(final H8Constants.Command cmd, final String... params) {
        sendCommand(cmd.getCode(), params);
    }

    /**
     * 发送原始命令码到无人机
     *
     * @param cmdCode 命令码数值
     * @param params  附加参数键值对 (可为 null)，偶数位为 key，奇数位为 value
     */
    public void sendCommand(final int cmdCode, final String... params) {
        if (!mIsConnected || mWriter == null) {
            Log.w(TAG, "发送失败: TCP 未连接");
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("CMD", cmdCode);

            // 附加参数
            if (params != null && params.length >= 2) {
                for (int i = 0; i < params.length - 1; i += 2) {
                    json.put(params[i], params[i + 1]);
                }
            }

            String cmdStr = json.toString() + "\n";
            Log.d(TAG, "TCP 发送: " + cmdStr.trim());

            synchronized (mWriter) {
                mWriter.write(cmdStr);
                mWriter.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "发送命令失败: " + e.getMessage());
        }
    }

    /**
     * 发送原始 JSON 字符串 (以换行结尾)
     *
     * @param jsonStr 完整的 JSON 字符串
     */
    public void sendRawCommand(final String jsonStr) {
        if (!mIsConnected || mWriter == null) {
            Log.w(TAG, "发送失败: TCP 未连接");
            return;
        }

        try {
            String cmdStr = jsonStr.endsWith("\n") ? jsonStr : jsonStr + "\n";
            Log.d(TAG, "TCP 发送(原始): " + cmdStr.trim());

            synchronized (mWriter) {
                mWriter.write(cmdStr);
                mWriter.flush();
            }
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
