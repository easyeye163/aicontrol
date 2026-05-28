package com.aicontrol.android.m8.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * M8/H8 Drone connection configuration utility.
 * Stores and retrieves IP, video, control, and FTP port settings
 * using SharedPreferences (same style as KVUtils/DroneConfig).
 *
 * Based on decompiled com.h8 (HY-Chip Technology) APK analysis:
 * - Video stream: Live555 RTSP/RTP H.264/H.265 via UDP (port 1563)
 * - Control commands: TCP JSON-based commands (port 4646)
 * - Flight telemetry: UDP (port 19798)
 * - FTP file transfer: port 21 (user: HY819, pass: 1663819)
 * - Default IP: 192.168.100.1
 */
public class M8Config {

    private static final String TAG = "M8Config";

    // Default values based on decompiled H8/M8 APK (HY-Chip Technology)
    public static final String DEFAULT_IP = "192.168.100.1";
    public static final int DEFAULT_UDP_PORT = 1563;     // Video stream port (UDP, Live555 RTSP/RTP H.264/H.265)
    public static final int DEFAULT_TCP_PORT = 4646;     // Control command port (TCP, JSON commands)
    public static final int DEFAULT_FTP_PORT = 21;       // FTP file transfer port

    // SharedPreferences
    private static final String PREF_NAME = "m8_config";
    private static final String KEY_IP = "m8_ip";
    private static final String KEY_UDP_PORT = "m8_video_port";
    private static final String KEY_TCP_PORT = "m8_tcp_port";
    private static final String KEY_FTP_PORT = "m8_ftp_port";

    public static class Config {
        public String ip;
        public int udpPort;
        public int tcpPort;
        public int ftpPort;

        public Config() {
            this.ip = DEFAULT_IP;
            this.udpPort = DEFAULT_UDP_PORT;
            this.tcpPort = DEFAULT_TCP_PORT;
            this.ftpPort = DEFAULT_FTP_PORT;
        }

        public Config(String ip, int udpPort, int tcpPort, int ftpPort) {
            this.ip = ip;
            this.udpPort = udpPort;
            this.tcpPort = tcpPort;
            this.ftpPort = ftpPort;
        }

        /** Get the video stream address (UDP port for Live555 RTSP/RTP) */
        public String getVideoStreamUrl() {
            return ip + ":" + udpPort;
        }

        /** Get the video RTSP URL (if applicable) */
        public String getRtspUrl() {
            return "rtsp://" + ip + ":" + udpPort + "/live";
        }

        /** Get the TCP control address */
        public String getTcpAddress() {
            return ip + ":" + tcpPort;
        }

        /** Get the FTP address */
        public String getFtpAddress() {
            return ip + ":" + ftpPort;
        }
    }

    /**
     * Load M8 config from SharedPreferences.
     */
    public static Config loadConfig(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Config config = new Config();
        config.ip = sp.getString(KEY_IP, DEFAULT_IP);
        config.udpPort = sp.getInt(KEY_UDP_PORT, DEFAULT_UDP_PORT);
        config.tcpPort = sp.getInt(KEY_TCP_PORT, DEFAULT_TCP_PORT);
        config.ftpPort = sp.getInt(KEY_FTP_PORT, DEFAULT_FTP_PORT);
        return config;
    }

    /**
     * Save M8 config to SharedPreferences.
     */
    public static void saveConfig(Context context, Config config) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_IP, config.ip)
                .putInt(KEY_UDP_PORT, config.udpPort)
                .putInt(KEY_TCP_PORT, config.tcpPort)
                .putInt(KEY_FTP_PORT, config.ftpPort)
                .apply();
        Log.d(TAG, "Config saved: ip=" + config.ip + " video=" + config.udpPort
                + " ctrl=" + config.tcpPort + " ftp=" + config.ftpPort);
    }

    /**
     * Get the configured IP address.
     */
    public static String getIp(Context context) {
        return loadConfig(context).ip;
    }

    /**
     * Get the configured video stream port (UDP).
     */
    public static int getVideoPort(Context context) {
        return loadConfig(context).udpPort;
    }

    /**
     * @deprecated Use {@link #getVideoPort(Context)} instead.
     */
    public static int getUdpPort(Context context) {
        return getVideoPort(context);
    }

    /**
     * Get the configured TCP port.
     */
    public static int getTcpPort(Context context) {
        return loadConfig(context).tcpPort;
    }

    /**
     * Get the configured FTP port.
     */
    public static int getFtpPort(Context context) {
        return loadConfig(context).ftpPort;
    }
}
