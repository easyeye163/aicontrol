package com.aicontrol.android.aircam.h8;

/**
 * H8 无人机协议常量定义
 * 基于 HFun 无人机 APK 逆向分析结果
 *
 * 包含:
 * - 网络地址和端口
 * - UDP 握手魔数
 * - 缓冲区大小
 * - 超时/重连间隔
 * - 命令码枚举
 * - 平台类型枚举
 * - 分辨率枚举
 */
public final class H8Constants {

    private H8Constants() {
        // 工具类，禁止实例化
    }

    // ======================== 网络常量 ========================

    /** H8 无人机默认 IP 地址 (AP 热点模式) */
    public static final String DRONE_IP = "192.168.100.1";

    /** TCP 控制通道端口 (JSON 命令通道) */
    public static final int TCP_PORT = 4646;

    /** UDP 视频流端口 (RTP/H.264 数据) */
    public static final int UDP_VIDEO_PORT = 1563;

    // ======================== 握手常量 ========================

    /** UDP 连接握手魔数: D8 C0 D9，发送后无人机开始推送视频流 */
    public static final byte[] HANDSHAKE_MAGIC = {(byte) 0xD8, (byte) 0xC0, (byte) 0xD9};

    // ======================== 缓冲区与超时 ========================

    /** UDP 接收缓冲区大小 (100KB，足以容纳单个 RTP 包) */
    public static final int RECEIVE_BUFFER_SIZE = 102400;

    /** TCP 连接超时 (毫秒) */
    public static final int CONNECT_TIMEOUT_MS = 3000;

    /** TCP 断线自动重连间隔 (毫秒) */
    public static final int RECONNECT_INTERVAL_MS = 200;

    /** UDP 握手失败后重试延迟 (毫秒) */
    public static final int HANDSHAKE_RETRY_DELAY_MS = 40;

    // ======================== 命令码枚举 ========================

    /**
     * H8 TCP 命令码枚举
     * 基于 o2.f.c (Config) 枚举的 ordinal 值
     */
    public enum Command {
        /** 获取系统参数 (固件信息等) - 服务器连接后自动推送 */
        SYS_PARAM_GET(0),
        /** 设置状态 */
        STATE_SET(1),
        /** 开启预览编码 (视频预览) */
        VID_ENC_PREVIEW_ON(2),
        /** 关闭预览编码 */
        VID_ENC_PREVIEW_OFF(3),
        /** 开始录制/编码 */
        VID_ENC_START(4),
        /** 停止录制/编码 */
        VID_ENC_STOP(5),
        /** 暂停录制 */
        VID_ENC_PAUSE(6),
        /** 恢复录制 */
        VID_ENC_RESUME(7),
        /** 拍照 */
        VID_ENC_CAPTURE(11),
        /** 设置分辨率 */
        RESOLUTION_SET(16),
        /** 设置色相 */
        HUE_SET(20),
        /** 设置饱和度 */
        SATURATION_SET(21),
        /** 设置亮度 */
        BRIGHTNESS_SET(22),
        /** 设置对比度 */
        CONTRAST_SET(23),
        /** 设置锐度 */
        SHARPNESS_SET(24),
        /** 设置 ISO */
        ISO_SET(25),
        /** 设置帧率 */
        FRM_RATE_SET(28),
        /** 设置日期时间 */
        DATE_TIME_SET(29),
        /** 获取固件版本 */
        FIRMWARE_VERSION_GET(39),
        /** 获取 SD 卡信息 */
        CARD_INFO_GET(63),
        /** 通知无人机断开网络 */
        NET_DISCONN(67),
        /** 绑定 APP 的 UDP 端口 */
        CMD_BIND_APP_UDP(94),
        /** TCP 连接已建立通知 */
        CMD_TCP_CONNECTED(116),
        /** 视频流格式信息 */
        CMD_STREAM_FORMAT(143),
        /** 断开 (MAX sentinel) */
        MAX(78);

        private final int code;

        Command(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        /**
         * 根据命令码数值查找对应的枚举
         *
         * @param code 命令码数值
         * @return 对应的 Command 枚举，未知则返回 null
         */
        public static Command fromCode(int code) {
            for (Command cmd : values()) {
                if (cmd.code == code) {
                    return cmd;
                }
            }
            return null;
        }
    }

    // ======================== 平台类型枚举 ========================

    /**
     * HFun 系列无人机平台类型
     * 用于解析固件字符串中的平台标识
     */
    public enum Platform {
        H8(0),
        M8(1),
        A7(2),
        A9(4),
        A6(5),
        A11(6);

        private final int code;

        Platform(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static Platform fromCode(int code) {
            for (Platform p : values()) {
                if (p.code == code) {
                    return p;
                }
            }
            return null;
        }
    }

    // ======================== 分辨率枚举 ========================

    /**
     * 视频分辨率类型
     */
    public enum Resolution {
        VGA(0),
        QVGA(1),
        HD_720P(2),
        FULL_HD_1080P(3),
        QHD_2K(4),
        UHD_4K(5);

        private final int code;

        Resolution(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static Resolution fromCode(int code) {
            for (Resolution r : values()) {
                if (r.code == code) {
                    return r;
                }
            }
            return null;
        }
    }
}
