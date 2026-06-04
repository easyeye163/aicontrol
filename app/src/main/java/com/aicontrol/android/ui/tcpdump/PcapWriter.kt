package com.aicontrol.android.ui.tcpdump

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.System.currentTimeMillis

/**
 * PCAP 文件写入器
 * 支持写入标准 PCAP 格式文件，可用 Wireshark 直接打开
 */
class PcapWriter(private val file: File) {

    private val dos: DataOutputStream

    init {
        dos = DataOutputStream(FileOutputStream(file))
        writeGlobalHeader()
    }

    /**
     * 写入 PCAP 全局头（文件头）
     */
    private fun writeGlobalHeader() {
        // Magic Number (little-endian: 0xa1b2c3d4)
        dos.writeInt(0xa1b2c3d4)
        // Major Version
        dos.writeShort(2)
        // Minor Version
        dos.writeShort(4)
        // Reserved1 (thiszone)
        dos.writeInt(0)
        // Reserved2 (sigfigs)
        dos.writeInt(0)
        // Snaplen (max packet length)
        dos.writeInt(65535)
        // Link Type: 101 = Raw IP (no Ethernet header)
        dos.writeInt(101)
    }

    /**
     * 追加一个 IP 数据包
     * @param packetData 原始 IP 包字节数组（包含 IP 头）
     * @param length 实际包长
     */
    @Synchronized
    fun writePacket(packetData: ByteArray, length: Int) {
        try {
            val ts = currentTimeMillis()
            val tsSec = (ts / 1000).toInt()
            val tsUsec = ((ts % 1000) * 1000).toInt()

            // Packet Header
            dos.writeInt(tsSec)
            dos.writeInt(tsUsec)
            // Included length
            dos.writeInt(length)
            // Original length
            dos.writeInt(length)

            // Packet Data
            dos.write(packetData, 0, length)
        } catch (e: IOException) {
            // ignore write errors
        }
    }

    /**
     * 关闭文件
     */
    fun close() {
        try {
            dos.close()
        } catch (e: IOException) {
            // ignore
        }
    }
}
