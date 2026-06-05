package com.aicontrol.android.ui.car

/**
 * 视频流协议枚举
 *
 * 定义小车/无人机摄像头视频流的传输协议类型。
 * 用于设置界面选择和小车控制界面的连接逻辑。
 */
enum class StreamProtocol(
    val key: String,
    val label: String,
    val description: String,
    /** 默认端口号（仅部分协议有意义） */
    val defaultPort: Int = 80,
    /** 该协议的解码实现状态 */
    val status: ProtocolStatus
) {
    /**
     * 1. H264 裸流
     * TCP 直连接收 H264 NALU 裸流，使用 MediaCodec 硬解码。
     * 当前已实现。
     */
    H264_RAW(
        key = "h264_raw",
        label = "H264裸流",
        description = "TCP 直连接收 H264 NALU 裸流，MediaCodec 硬解码",
        defaultPort = 80,
        status = ProtocolStatus.IMPLEMENTED
    ),

    /**
     * 2. MJPG 压缩
     * 通过 HTTP MJPEG 流获取 JPEG 帧序列，逐帧解码显示。
     * 标准的 MJPEG-over-HTTP 协议，大部分 IP 摄像头支持。
     */
    MJPEG(
        key = "mjpeg",
        label = "MJPG压缩",
        description = "HTTP MJPEG 流，逐帧 JPEG 解码显示（IP 摄像头通用）",
        defaultPort = 8080,
        status = ProtocolStatus.IMPLEMENTED
    ),

    /**
     * 3. OpenIPC
     * OpenIPC 固件协议，支持多种摄像头芯片。
     * 基于 HTTP 接口获取 H264/MJPEG 流。
     * https://openipc.org
     */
    OPENIPC(
        key = "openipc",
        label = "OpenIPC",
        description = "OpenIPC 固件协议，支持多种摄像头芯片（HTTP 接口）",
        defaultPort = 80,
        status = ProtocolStatus.IMPLEMENTED
    ),

    /**
     * 4. H265 安佳协议
     * 使用安佳(Anjia)私有协议传输 H265 编码流。
     * 需要安佳专用解码库支持。
     */
    H265_ANJIA(
        key = "h265_anjia",
        label = "H265安佳协议",
        description = "安佳私有协议传输 H265 编码流（需要专用解码库）",
        defaultPort = 7070,
        status = ProtocolStatus.PENDING
    ),

    /**
     * 5. H264 (WebSocket) 拉流
     * 通过 WebSocket 连接接收 H264 编码流。
     * 适用于 Web 端转发的视频流。
     */
    H264_WS(
        key = "h264_ws",
        label = "H264(WS)拉流",
        description = "WebSocket 连接接收 H264 编码流（Web 转发适用）",
        defaultPort = 8080,
        status = ProtocolStatus.PENDING
    ),

    /**
     * 6. H264 大华/熊迈协议
     * 大华(Dahua)或熊迈(Xiongmai/XM)私有协议传输 H264 流。
     * 需要对应的私有协议解析库。
     */
    H264_DAUA(
        key = "h264_dahua",
        label = "H264大华/熊迈协议",
        description = "大华/熊迈私有协议传输 H264 流（需要专用解析库）",
        defaultPort = 34567,
        status = ProtocolStatus.PENDING
    ),

    /**
     * 7. H264 (RTSP) 拉流
     * 标准 RTSP 协议拉取 H264 流。
     * 适用于标准 IP 摄像头、NVR 等设备。
     * URL 格式: rtsp://ip:port/stream1
     */
    H264_RTSP(
        key = "h264_rtsp",
        label = "H264(RTSP)拉流",
        description = "标准 RTSP 协议拉取 H264 流（IP 摄像头/NVR 通用）",
        defaultPort = 554,
        status = ProtocolStatus.IMPLEMENTED
    ),

    /**
     * 8. Anjia (Low Latency)
     * 安佳低延迟协议，针对实时遥控场景优化。
     * 基于 UDP 的低延迟传输方案。
     */
    ANJIA_LOW_LATENCY(
        key = "anjia_low_latency",
        label = "Anjia (Low Latency)",
        description = "安佳低延迟协议，基于 UDP 实时传输（遥控场景优化）",
        defaultPort = 7071,
        status = ProtocolStatus.PENDING
    );

    companion object {
        /**
         * 根据 key 查找协议枚举，未找到返回默认的 H264_RAW
         */
        fun fromKey(key: String): StreamProtocol {
            return values().find { it.key == key } ?: H264_RAW
        }

        /**
         * 获取所有协议的标签列表（用于 Spinner 显示）
         */
        fun labels(): Array<String> = values().map { it.label }.toTypedArray()

        /**
         * 获取所有协议的描述列表
         */
        fun descriptions(): Array<String> = values().map { it.description }.toTypedArray()
    }
}

/**
 * 协议实现状态
 */
enum class ProtocolStatus {
    /** 已实现 - 可以正常使用 */
    IMPLEMENTED,
    /** 待实现 - 尚未开发，选择时会提示 */
    PENDING
}
