package com.aicontrol.android.ui.tcpdump

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * VpnService 抓包服务 - 无需 Root
 * 通过 Android VpnService 建立 VPN 隧道，拦截并记录所有网络流量
 * 同时转发数据包以保持网络连通性
 */
class PacketCaptureService : VpnService() {

    companion object {
        private const val TAG = "PacketCapture"

        // MTU
        private const val MAX_PACKET_SIZE = 32767

        // Action 常量
        const val ACTION_START = "com.aicontrol.android.action.CAPTURE_START"
        const val ACTION_STOP = "com.aicontrol.android.action.CAPTURE_STOP"
        const val ACTION_PACKET = "com.aicontrol.android.action.CAPTURE_PACKET"
        const val ACTION_STATUS = "com.aicontrol.android.action.CAPTURE_STATUS"

        // Intent Extra 常量
        const val EXTRA_SRC_IP = "src_ip"
        const val EXTRA_DST_IP = "dst_ip"
        const val EXTRA_SRC_PORT = "src_port"
        const val EXTRA_DST_PORT = "dst_port"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_LENGTH = "length"
        const val EXTRA_COUNT = "count"
        const val EXTRA_FILTER_IP = "filter_ip"
        const val EXTRA_STATUS = "status"
        const val EXTRA_INFO = "info"

        // 协议常量
        const val PROTO_ICMP = 1
        const val PROTO_TCP = 6
        const val PROTO_UDP = 17

        // 状态常量
        const val STATUS_STARTED = "started"
        const val STATUS_STOPPED = "stopped"
        const val STATUS_ERROR = "error"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunThread: Thread? = null
    private var isRunning = false

    private var packetCount = 0L
    private var filterIp: String? = null
    private var pcapWriter: PcapWriter? = null
    private var pcapFile: java.io.File? = null

    // TCP 连接池: "srcIp:srcPort-dstIp:dstPort" -> TcpConnection
    private val tcpConnections = ConcurrentHashMap<String, TcpConnection>()

    // UDP 转发缓存（简化版，使用独立 Socket）
    private var udpForwardSocket: DatagramSocket? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        if (isRunning) return

        filterIp = intent.getStringExtra(EXTRA_FILTER_IP)

        // 创建 PCAP 文件
        try {
            val dir = File(getExternalFilesDir(null), "pcap")
            dir.mkdirs()
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                .format(java.util.Date())
            pcapFile = File(dir, "capture_$timestamp.pcap")
            pcapWriter = PcapWriter(pcapFile!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create PCAP file", e)
        }

        // 建立 VPN 隧道
        val builder = Builder().apply {
            setSession("AiControl-Capture-${Build.MODEL}")
            // 虚拟 IP 地址
            addAddress("10.8.0.2", 24)
            // 路由所有流量
            addRoute("0.0.0.0", 0)
            // DNS 服务器
            addDnsServer("8.8.8.8")
            addDnsServer("8.8.4.4")
            // MTU
            setMtu(1500)
        }

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            broadcastStatus(STATUS_ERROR, "VPN 隧道建立失败")
            return
        }

        isRunning = true
        packetCount = 0

        broadcastStatus(STATUS_STARTED, "开始抓包")

        // 启动 UDP 转发 Socket
        try {
            udpForwardSocket = DatagramSocket()
            protect(udpForwardSocket)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create UDP forward socket", e)
        }

        // 启动 TUN 读取线程
        tunThread = Thread({
            readTunLoop()
        }, "TunReader").apply {
            isDaemon = true
            start()
        }
    }

    private fun readTunLoop() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val fis = FileInputStream(fd)
        val fos = FileOutputStream(fd)
        val buffer = ByteArray(MAX_PACKET_SIZE)

        while (isRunning) {
            try {
                val length = fis.read(buffer)
                if (length > 0 && length >= 20) {
                    val packetCopy = buffer.copyOf(length)

                    // 写入 PCAP
                    pcapWriter?.writePacket(packetCopy, length)

                    // 解析并广播
                    parseAndBroadcast(packetCopy, length)

                    // 转发数据包
                    forwardPacket(packetCopy, length, fos)
                }
            } catch (e: IOException) {
                if (isRunning) {
                    Log.e(TAG, "TUN read error", e)
                }
            }
        }

        try { fis.close() } catch (_: Exception) {}
        try { fos.close() } catch (_: Exception) {}
    }

    /**
     * 解析 IPv4 包头并广播包信息
     */
    private fun parseAndBroadcast(data: ByteArray, length: Int) {
        val version = (data[0].toInt() shr 4) and 0x0F
        if (version != 4) return

        val ihl = (data[0].toInt() and 0x0F) * 4
        val protocol = data[9].toInt() and 0xFF

        val srcIp = intToIp(
            (data[12].toInt() and 0xFF shl 24) or
            (data[13].toInt() and 0xFF shl 16) or
            (data[14].toInt() and 0xFF shl 8) or
            data[15].toInt() and 0xFF
        )
        val dstIp = intToIp(
            (data[16].toInt() and 0xFF shl 24) or
            (data[17].toInt() and 0xFF shl 16) or
            (data[18].toInt() and 0xFF shl 8) or
            data[19].toInt() and 0xFF
        )

        var srcPort = 0
        var dstPort = 0
        var protoName = "UNKNOWN"

        when (protocol) {
            PROTO_TCP -> {
                protoName = "TCP"
                if (length > ihl + 4) {
                    srcPort = (data[ihl].toInt() and 0xFF) shl 8 or (data[ihl + 1].toInt() and 0xFF)
                    dstPort = (data[ihl + 2].toInt() and 0xFF) shl 8 or (data[ihl + 3].toInt() and 0xFF)
                }
            }
            PROTO_UDP -> {
                protoName = "UDP"
                if (length > ihl + 4) {
                    srcPort = (data[ihl].toInt() and 0xFF) shl 8 or (data[ihl + 1].toInt() and 0xFF)
                    dstPort = (data[ihl + 2].toInt() and 0xFF) shl 8 or (data[ihl + 3].toInt() and 0xFF)
                }
            }
            PROTO_ICMP -> protoName = "ICMP"
        }

        // 过滤
        if (!filterIp.isNullOrEmpty()) {
            if (srcIp != filterIp && dstIp != filterIp) return
        }

        packetCount++

        val broadcast = Intent(ACTION_PACKET).apply {
            putExtra(EXTRA_SRC_IP, srcIp)
            putExtra(EXTRA_DST_IP, dstIp)
            putExtra(EXTRA_SRC_PORT, srcPort)
            putExtra(EXTRA_DST_PORT, dstPort)
            putExtra(EXTRA_PROTOCOL, protoName)
            putExtra(EXTRA_LENGTH, length)
            putExtra(EXTRA_COUNT, packetCount)
        }
        sendBroadcast(broadcast)
    }

    /**
     * 转发数据包到真实网络
     * UDP: 通过 protected DatagramSocket 转发
     * TCP: 通过 protected Socket 连接池转发
     */
    private fun forwardPacket(data: ByteArray, length: Int, tunOut: FileOutputStream) {
        val version = (data[0].toInt() shr 4) and 0x0F
        if (version != 4) return

        val ihl = (data[0].toInt() and 0x0F) * 4
        val protocol = data[9].toInt() and 0xFF
        val totalLength = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)

        val dstIp = intToIp(
            (data[16].toInt() and 0xFF shl 24) or
            (data[17].toInt() and 0xFF shl 16) or
            (data[18].toInt() and 0xFF shl 8) or
            data[19].toInt() and 0xFF
        )
        val srcIp = intToIp(
            (data[12].toInt() and 0xFF shl 24) or
            (data[13].toInt() and 0xFF shl 16) or
            (data[14].toInt() and 0xFF shl 8) or
            data[15].toInt() and 0xFF
        )

        try {
            when (protocol) {
                PROTO_UDP -> forwardUdp(data, ihl, length, dstIp, tunOut)
                PROTO_TCP -> forwardTcp(data, ihl, length, srcIp, dstIp, tunOut)
                // ICMP: 不转发
            }
        } catch (e: Exception) {
            // 转发失败不阻塞主循环
            Log.w(TAG, "Forward error: ${e.message}")
        }
    }

    /**
     * UDP 转发
     */
    private fun forwardUdp(data: ByteArray, ihl: Int, length: Int, dstIp: String, tunOut: FileOutputStream) {
        val socket = udpForwardSocket ?: return
        val srcPort = (data[ihl].toInt() and 0xFF) shl 8 or (data[ihl + 1].toInt() and 0xFF)
        val dstPort = (data[ihl + 2].toInt() and 0xFF) shl 8 or (data[ihl + 3].toInt() and 0xFF)
        val payloadLength = length - ihl - 8
        if (payloadLength <= 0) return

        // 发送 UDP 数据包到目标
        val payload = data.copyOfRange(ihl + 8, length)
        val packet = DatagramPacket(payload, payload.size, InetAddress.getByName(dstIp), dstPort)
        socket.send(packet)

        // 接收响应（带超时）
        socket.soTimeout = 100
        try {
            val recvBuf = ByteArray(65535)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            socket.receive(recvPacket)

            // 构建返回 IP + UDP 包
            val udpLen = recvPacket.length + 8
            val ipTotalLen = 20 + udpLen
            val respPacket = ByteArray(ipTotalLen)
            // IPv4 头（简化）
            respPacket[0] = 0x45 // Version=4, IHL=5
            respPacket[1] = 0x00 // TOS
            respPacket[2] = (ipTotalLen shr 8).toByte()
            respPacket[3] = ipTotalLen.toByte()
            respPacket[4] = 0x00; respPacket[5] = 0x01 // ID
            respPacket[6] = 0x00; respPacket[7] = 0x00 // Flags/Frag
            respPacket[8] = 64 // TTL
            respPacket[9] = 17 // Protocol=UDP
            respPacket[10] = 0x00; respPacket[11] = 0x00 // Checksum (skip)
            // Src IP = original dst
            val dstBytes = dstIp.split(".").map { it.toInt().toByte() }.toByteArray()
            System.arraycopy(dstBytes, 0, respPacket, 12, 4)
            // Dst IP = original src (虚拟 VPN IP)
            respPacket[16] = 10; respPacket[17] = 8; respPacket[18] = 0; respPacket[19] = 2

            // UDP 头
            respPacket[ihl] = (recvPacket.port shr 8).toByte()
            respPacket[ihl + 1] = recvPacket.port.toByte()
            respPacket[ihl + 2] = (srcPort shr 8).toByte()
            respPacket[ihl + 3] = srcPort.toByte()
            respPacket[ihl + 4] = (udpLen shr 8).toByte()
            respPacket[ihl + 5] = udpLen.toByte()
            respPacket[ihl + 6] = 0x00 // Checksum (skip)
            respPacket[ihl + 7] = 0x00
            System.arraycopy(recvPacket.data, 0, respPacket, ihl + 8, recvPacket.length)

            tunOut.write(respPacket, 0, ipTotalLen)
        } catch (_: Exception) {
            // 超时，无响应
        }
    }

    /**
     * TCP 转发 - 连接池模式
     */
    private fun forwardTcp(data: ByteArray, ihl: Int, length: Int, srcIp: String, dstIp: String, tunOut: FileOutputStream) {
        val srcPort = (data[ihl].toInt() and 0xFF) shl 8 or (data[ihl + 1].toInt() and 0xFF)
        val dstPort = (data[ihl + 2].toInt() and 0xFF) shl 8 or (data[ihl + 3].toInt() and 0xFF)

        // TCP flags
        val dataOffset = ((data[ihl + 12].toInt() shr 4) and 0x0F) * 4
        val flags = data[ihl + 13].toInt() and 0xFF
        val isSyn = (flags and 0x02) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0

        val connKey = "$srcIp:$srcPort-$dstIp:$dstPort"

        try {
            if (isRst) {
                tcpConnections.remove(connKey)?.close()
                return
            }

            if (isFin) {
                tcpConnections.remove(connKey)?.close()
                return
            }

            // 获取或创建连接
            val conn = tcpConnections.getOrPut(connKey) {
                TcpConnection(dstIp, dstPort).also {
                    protect(it.socket)
                    it.connect()
                    // 读取响应线程
                    Thread({
                        it.readResponse(tunOut, srcIp, srcPort, pcapWriter)
                    }, "TcpResp-$connKey").apply {
                        isDaemon = true
                        start()
                    }
                }
            }

            // 提取 TCP payload
            val payloadOffset = ihl + dataOffset
            if (payloadOffset < length) {
                val payload = data.copyOfRange(payloadOffset, length)
                if (payload.isNotEmpty()) {
                    conn.send(payload)
                }
            }
        } catch (e: Exception) {
            tcpConnections.remove(connKey)?.close()
            Log.w(TAG, "TCP forward error: $connKey - ${e.message}")
        }
    }

    private fun stopCapture() {
        isRunning = false
        tunThread?.join(2000)
        tunThread = null

        // 关闭所有 TCP 连接
        tcpConnections.values.forEach { it.close() }
        tcpConnections.clear()

        // 关闭 UDP Socket
        udpForwardSocket?.close()
        udpForwardSocket = null

        // 关闭 VPN
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null

        // 关闭 PCAP
        pcapWriter?.close()

        val pcapPath = pcapFile?.absolutePath ?: ""
        broadcastStatus(STATUS_STOPPED, "抓包已停止 - $packetCount 个数据包\nPCAP: $pcapPath")

        stopSelf()
    }

    private fun broadcastStatus(status: String, info: String) {
        sendBroadcast(Intent(ACTION_STATUS).apply {
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_INFO, info)
            putExtra(EXTRA_COUNT, packetCount)
        })
    }

    private fun intToIp(ip: Int): String {
        return "${ip ushr 24 and 0xFF}.${ip ushr 16 and 0xFF}.${ip ushr 8 and 0xFF}.${ip and 0xFF}"
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    /**
     * TCP 连接包装器
     */
    inner class TcpConnection(val host: String, val port: Int) {
        val socket = Socket()
        val outStream = socket.getOutputStream()
        val inStream = socket.getInputStream()

        fun connect() {
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 200
        }

        fun send(data: ByteArray) {
            outStream.write(data)
            outStream.flush()
        }

        /**
         * 读取服务器响应，构建 IP+TCP 包写回 TUN
         */
        fun readResponse(tunOut: FileOutputStream, origSrcIp: String, origSrcPort: Int, pcapWriter: PcapWriter?) {
            try {
                while (isRunning && !socket.isClosed) {
                    val buf = ByteArray(4096)
                    val n = try { inStream.read(buf) } catch (_: Exception) { -1 }
                    if (n <= 0) break

                    // 构建 IP + TCP 返回包
                    val tcpHeaderLen = 20
                    val totalLen = 20 + tcpHeaderLen + n
                    val pkt = ByteArray(totalLen)

                    // IPv4 头
                    pkt[0] = 0x45; pkt[1] = 0x00
                    pkt[2] = (totalLen shr 8).toByte(); pkt[3] = totalLen.toByte()
                    pkt[4] = 0x00; pkt[5] = 0x01
                    pkt[6] = 0x00; pkt[7] = 0x00
                    pkt[8] = 64; pkt[9] = 6 // TCP
                    pkt[10] = 0x00; pkt[11] = 0x00
                    // Src IP = remote host
                    val bytes = host.split(".").map { it.toInt().toByte() }.toByteArray()
                    System.arraycopy(bytes, 0, pkt, 12, 4)
                    // Dst IP = VPN client
                    pkt[16] = 10; pkt[17] = 8; pkt[18] = 0; pkt[19] = 2

                    // TCP 头
                    pkt[20] = (port shr 8).toByte(); pkt[21] = port.toByte()
                    pkt[22] = (origSrcPort shr 8).toByte(); pkt[23] = origSrcPort.toByte()
                    pkt[24] = 0x00; pkt[25] = 0x01 // seq
                    pkt[26] = 0x00; pkt[27] = 0x01 // ack
                    pkt[28] = 0x50 // data offset=5
                    pkt[29] = 0x18 // PSH+ACK
                    pkt[30] = 0xFFFF; pkt[31] = 0xFFFF // window
                    pkt[32] = 0x00; pkt[33] = 0x00 // checksum
                    pkt[34] = 0x00; pkt[35] = 0x00 // urgent

                    System.arraycopy(buf, 0, pkt, 40, n)

                    tunOut.write(pkt, 0, totalLen)

                    // 写 PCAP
                    try { pcapWriter?.writePacket(pkt, totalLen) } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                // 连接关闭
            }
        }

        fun close() {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
