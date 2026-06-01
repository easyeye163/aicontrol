package com.aicontrol.android.aircam.h8;

/**
 * H8 TCP 控制通道观察者接口
 *
 * 用于接收 TCP 连接状态变化和命令响应的回调通知。
 * H8TcpManager 通过此接口将无人机返回的 JSON 命令解析后
 * 分发给注册的观察者。
 *
 * 使用方式:
 * <pre>
 *   tcpManager.registerObserver(this);
 *   // ...
 *   tcpManager.unregisterObserver(this);
 * </pre>
 */
public interface H8TcpObserver {

    /**
     * TCP 连接成功建立回调
     * 连接成功后通常会发送 CMD:0 查询固件信息
     */
    void onTcpConnected();

    /**
     * TCP 连接断开回调
     * 断开后 H8TcpManager 会自动尝试重连 (间隔 200ms)
     */
    void onTcpDisconnected();

    /**
     * 收到无人机命令响应回调
     *
     * @param cmd    命令码 (参见 H8Constants.Command)
     * @param result 结果码 (0=成功)
     * @param param  附加参数字符串 (如固件版本信息)
     */
    void onTcpCommand(int cmd, int result, String param);

    /**
     * 收到原始 TCP 数据回调
     * 用于调试或特殊数据处理
     *
     * @param data 原始字节数据
     */
    void onTcpData(byte[] data);
}
