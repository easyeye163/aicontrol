package com.aicontrol.android.m8.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * M8 Drone connection configuration utility.
 * Stores and retrieves IP, UDP, TCP, and FTP port settings
 * using SharedPreferences (same style as KVUtils/DroneConfig).
 *
 * M8 drones typically communicate via:
 * - HTTP MJPEG video stream on UDP port
 * - HTTP GET commands on TCP port for control
 * - FTP for media file transfer
 */
public class M8Config {

    private static final String TAG = "M8Config";

    // Default values for M8 drone
    public static final String DEFAULT_IP = "192.168.1.1";
    public static final int DEFAULT_UDP_PORT = 8554;    // Video stream port (MJPEG)
    public static final int DEFAULT_TCP_PORT = 8080;     // Control command port
    public static final int DEFAULT_FTP_PORT = 21;       // FTP file transfer port

    // SharedPreferences
    private static final String PREF_NAME = "m8_config";
    private static final String KEY_IP = "m8_ip";
    private static final String KEY_UDP_PORT = "m8_udp_port";
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

        /** Get the MJPEG video stream URL */
        public String getVideoStreamUrl() {
            return "http://" + ip + ":" + udpPort + "/live";
        }

        /** Get the snapshot URL */
        public String getSnapshotUrl() {
            return "http://" + ip + ":" + udpPort + "/capture";
        }

        /** Build a control command URL */
        public String getCommandUrl(String cmd) {
            return "http://" + ip + ":" + tcpPort + "/?cmd=" + cmd;
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
        Log.d(TAG, "Config saved: ip=" + config.ip + " udp=" + config.udpPort
                + " tcp=" + config.tcpPort + " ftp=" + config.ftpPort);
    }

    /**
     * Get the configured IP address.
     */
    public static String getIp(Context context) {
        return loadConfig(context).ip;
    }

    /**
     * Get the configured UDP port.
     */
    public static int getUdpPort(Context context) {
        return loadConfig(context).udpPort;
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
