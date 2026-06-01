package com.aicontrol.android.aircam.h8;

import android.util.Log;

/**
 * H8 RTP/H.264 去包化器 (RFC 6184)
 *
 * 将 RTP 负载中的 H.264 NAL 单元提取出来。
 * H8 无人机使用标准 RTP 封装 H.264 视频流。
 *
 * RTP 包头结构 (12 字节固定 + 可选扩展):
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                         timestamp                           |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |           synchronization source (SSRC) identifier            |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * 支持的 H.264 NAL 单元类型 (data[1] & 0x1F):
 * - type 1~23: 单一 NAL 单元包
 * - type 24 (STAP-A): 聚合包，包含多个 NAL 单元
 * - type 28 (FU-A): 分片包，将一个大 NAL 单元拆分到多个 RTP 包中
 *
 * 使用方式:
 * <pre>
 *   H8RtpDepacketizer depacketizer = new H8RtpDepacketizer();
 *   byte[] nal = depacketizer.parseRtpPacket(rtpData, rtpLength);
 *   if (nal != null) {
 *       decoder.offerData(nal);
 *   }
 * </pre>
 */
public class H8RtpDepacketizer {

    private static final String TAG = "H8Rtp";

    // ======================== RTP 头部常量 ========================

    /** RTP 头部最小长度 (12 字节) */
    private static final int RTP_HEADER_MIN_SIZE = 12;

    /** RTP 版本号 (固定为 2) */
    private static final int RTP_VERSION = 2;

    /** NAL 单元类型 1~23: 单一 NAL 单元 */
    private static final int NAL_TYPE_STAP_A = 24;
    /** NAL 单元类型 28: FU-A 分片 */
    private static final int NAL_TYPE_FU_A = 28;

    // ======================== FU-A 分片重组 ========================

    /** 当前 FU-A 重组缓冲区 */
    private byte[] mFuReassemblyBuffer;

    /** 当前 FU-A 重组写入位置 */
    private int mFuReassemblyOffset;

    /** 上一个 FU-A 序列号 (用于丢包检测) */
    private int mFuLastSeqNum = -1;

    /** FU-A 分片类型: 起始帧的 NAL header */
    private byte mFuNalHeader;

    /** FU-A 是否正在重组中 */
    private boolean mFuReassembling = false;

    /** FU-A 丢包计数器 (调试用) */
    private int mFuDropCount = 0;

    // ======================== 公共接口 ========================

    /**
     * 解析 RTP 数据包，提取 H.264 NAL 单元
     *
     * @param data   RTP 数据包 (包含 RTP 头部)
     * @param length 数据包总长度
     * @return 完整的 H.264 NAL 单元字节数组；如果是不完整的 FU-A 分片则返回 null
     */
    public byte[] parseRtpPacket(byte[] data, int length) {
        if (data == null || length < RTP_HEADER_MIN_SIZE + 1) {
            return null;
        }

        // 解析 RTP 头部
        int rtpHeaderLen = parseRtpHeader(data, length);
        if (rtpHeaderLen < 0 || rtpHeaderLen >= length) {
            return null;
        }

        int payloadOffset = rtpHeaderLen;
        int payloadLen = length - payloadOffset;
        if (payloadLen <= 0) {
            return null;
        }

        // 获取 NAL 单元类型 (payload 第一个字节的低 5 位)
        int nalType = data[payloadOffset] & 0x1F;

        switch (nalType) {
            case NAL_TYPE_STAP_A:
                return parseSTAPA(data, payloadOffset, payloadLen);

            case NAL_TYPE_FU_A:
                return parseFUA(data, payloadOffset, payloadLen);

            default:
                // 单一 NAL 单元 (type 1~23): 直接返回整个 payload
                if (nalType >= 1 && nalType <= 23) {
                    byte[] nal = new byte[payloadLen];
                    System.arraycopy(data, payloadOffset, nal, 0, payloadLen);
                    return nal;
                }
                // type 0 或其他: 跳过 (可能是未定义的或序列参数)
                return null;
        }
    }

    /**
     * 重置分片重组状态 (用于 seek 或断线重连)
     */
    public void reset() {
        mFuReassembling = false;
        mFuReassemblyBuffer = null;
        mFuReassemblyOffset = 0;
        mFuLastSeqNum = -1;
        mFuDropCount = 0;
    }

    /**
     * @return FU-A 丢包计数 (调试用)
     */
    public int getDropCount() {
        return mFuDropCount;
    }

    // ======================== RTP 头部解析 ========================

    /**
     * 解析 RTP 头部，返回头部总长度
     *
     * @param data   RTP 数据
     * @param length 数据长度
     * @return 头部字节数，-1 表示无效
     */
    private int parseRtpHeader(byte[] data, int length) {
        byte firstByte = data[0];

        // 检查版本号 (前 2 位)
        int version = (firstByte >> 6) & 0x03;
        if (version != RTP_VERSION) {
            Log.w(TAG, "RTP 版本号不匹配: " + version);
            return -1;
        }

        // 解析 CSRC count (中间 4 位)
        int cc = firstByte & 0x0F;

        // 检查扩展标志 (第 1 字节第 5 位)
        boolean hasExtension = ((data[1] >> 7) & 0x01) == 1;

        int headerLen = RTP_HEADER_MIN_SIZE + (cc * 4);

        if (hasExtension && length > headerLen + 4) {
            // 扩展头部: 2 字节 profile + 2 字节 length
            int extLen = ((data[headerLen + 2] & 0xFF) << 8) | (data[headerLen + 3] & 0xFF);
            headerLen += 4 + (extLen * 4);
        }

        if (headerLen > length) {
            Log.w(TAG, "RTP 头部长度异常: headerLen=" + headerLen + " dataLen=" + length);
            return -1;
        }

        return headerLen;
    }

    // ======================== STAP-A 解析 ========================

    /**
     * 解析 STAP-A (Single-Time Aggregation Packet Type A)
     * 包含多个 NAL 单元，每个 NAL 前有 2 字节长度字段
     *
     * 结构: [STAP-A header] [16-bit NAL1 size] [NAL1 data] [16-bit NAL2 size] [NAL2 data] ...
     *
     * 注意: STAP-A 只返回第一个 NAL 单元 (简化处理)
     */
    private byte[] parseSTAPA(byte[] data, int offset, int len) {
        if (len < 3) return null;

        // 跳过 STAP-A 头部字节 (1 字节)
        int pos = offset + 1;

        // 读取第一个 NAL 单元的长度 (2 字节大端)
        if (pos + 2 > offset + len) return null;

        int nalSize = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;

        if (nalSize <= 0 || pos + nalSize > offset + len) {
            Log.w(TAG, "STAP-A NAL 大小异常: " + nalSize);
            return null;
        }

        byte[] nal = new byte[nalSize];
        System.arraycopy(data, pos, nal, 0, nalSize);

        Log.d(TAG, "STAP-A: 提取 NAL 单元，长度=" + nalSize);

        // 注意: 只返回第一个 NAL 单元。
        // 如需处理后续 NAL 单元，可以改为回调方式或返回列表
        return nal;
    }

    // ======================== FU-A 解析 ========================

    /**
     * 解析 FU-A (Fragmentation Unit Type A)
     *
     * FU-A 结构:
     * [1 byte FU indicator] [1 byte FU header] [payload data...]
     *
     * FU indicator: forbidden_zero_bit(1) | nal_ref_idc(2) | type(5=28)
     * FU header: S(1) | E(1) | R(1) | type(5)
     *   S=1: 起始分片
     *   E=1: 结束分片
     *   R=1: 保留 (必须为 0)
     *   type: NAL 单元类型
     *
     * 重组过程:
     * 1. 收到 S=1 的分片: 记录 NAL header，开始重组
     * 2. 收到 S=0, E=0 的分片: 追加数据
     * 3. 收到 E=1 的分片: 追加数据，完成重组
     *
     * @return 完整 NAL 单元，或 null (分片未完成)
     */
    private byte[] parseFUA(byte[] data, int offset, int len) {
        if (len < 3) return null;

        // FU indicator (1 字节) + FU header (1 字节) = 2 字节
        byte fuIndicator = data[offset];
        byte fuHeader = data[offset + 1];

        boolean startBit = ((fuHeader >> 7) & 0x01) == 1;
        boolean endBit = ((fuHeader >> 6) & 0x01) == 1;
        int nalType = fuHeader & 0x1F;

        // FU indicator 中的 nal_ref_idc (高 3 位: forbidden + nal_ref_idc)
        byte nalRefIdc = (byte) (fuIndicator & 0xE0);
        byte nalHeaderByte = (byte) (nalRefIdc | nalType);

        int payloadOffset = offset + 2;
        int payloadLen = len - 2;

        // ---- 起始分片 (S=1) ----
        if (startBit) {
            // 如果有未完成的重组，丢弃旧的
            if (mFuReassembling) {
                mFuDropCount++;
                Log.w(TAG, "FU-A: 丢弃未完成的分片 (dropCount=" + mFuDropCount + ")");
            }

            // 初始化重组缓冲区 (预分配 64KB)
            int bufferSize = Math.max(65536, payloadLen + 2);
            ensureReassemblyBuffer(bufferSize);

            // 写入 NAL header (1 字节)
            mFuReassemblyBuffer[0] = nalHeaderByte;
            mFuReassemblyOffset = 1;

            // 写入负载
            System.arraycopy(data, payloadOffset, mFuReassemblyBuffer, mFuReassemblyOffset, payloadLen);
            mFuReassemblyOffset += payloadLen;

            mFuNalHeader = nalHeaderByte;
            mFuReassembling = true;
            mFuLastSeqNum = -1;

            return null;
        }

        // ---- 中间/结束分片 (S=0) ----
        if (!mFuReassembling) {
            // 没有起始分片，跳过
            return null;
        }

        // 追加数据
        ensureReassemblyBuffer(mFuReassemblyOffset + payloadLen);
        System.arraycopy(data, payloadOffset, mFuReassemblyBuffer, mFuReassemblyOffset, payloadLen);
        mFuReassemblyOffset += payloadLen;

        // ---- 结束分片 (E=1) ----
        if (endBit) {
            // 重组完成
            mFuReassembling = false;
            byte[] result = new byte[mFuReassemblyOffset];
            System.arraycopy(mFuReassemblyBuffer, 0, result, 0, mFuReassemblyOffset);
            return result;
        }

        // 分片未完成
        return null;
    }

    // ======================== 缓冲区管理 ========================

    /**
     * 确保重组缓冲区足够大
     */
    private void ensureReassemblyBuffer(int requiredSize) {
        if (mFuReassemblyBuffer == null || mFuReassemblyBuffer.length < requiredSize) {
            byte[] newBuffer = new byte[requiredSize];
            if (mFuReassemblyBuffer != null && mFuReassemblyOffset > 0) {
                System.arraycopy(mFuReassemblyBuffer, 0, newBuffer, 0, mFuReassemblyOffset);
            }
            mFuReassemblyBuffer = newBuffer;
        }
    }
}
