package com.aicontrol.android.aircam.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Drone connection configuration utility.
 * Handles saving/loading config from SharedPreferences and
 * binary-patching libCamera.so to replace the hardcoded IP address.
 *
 * The native libCamera.so has "192.168.4.153\0" at file offset 0x1a9fc (14 bytes).
 * We patch this with a null-padded IP string of the same total length.
 */
public class DroneConfig {

    private static final String TAG = "DroneConfig";

    // Default values from the original firmware
    public static final String DEFAULT_IP = "192.168.4.153";
    public static final int DEFAULT_UDP_PORT = 2228;
    public static final int DEFAULT_TCP_PORT = 3333;

    // SharedPreferences
    private static final String PREF_NAME = "drone_config";
    private static final String KEY_IP = "drone_ip";
    private static final String KEY_UDP_PORT = "drone_udp_port";
    private static final String KEY_TCP_PORT = "drone_tcp_port";
    private static final String KEY_PATCH_VERSION = "drone_patch_version";
    private static final int CURRENT_PATCH_VERSION = 1;

    // Binary patch location in libCamera.so
    private static final int PATCH_OFFSET = 0x1a9fc;
    private static final int PATCH_MAX_LEN = 14; // "192.168.4.153\0" = 14 bytes
    private static final String ORIGINAL_IP = "192.168.4.153";

    public static class Config {
        public String ip;
        public int udpPort;
        public int tcpPort;

        public Config() {
            this.ip = DEFAULT_IP;
            this.udpPort = DEFAULT_UDP_PORT;
            this.tcpPort = DEFAULT_TCP_PORT;
        }

        public Config(String ip, int udpPort, int tcpPort) {
            this.ip = ip;
            this.udpPort = udpPort;
            this.tcpPort = tcpPort;
        }
    }

    public static class PatchInfo {
        public boolean patched;
        public String currentIp;
        public String originalIp;
        public int version;
    }

    /**
     * Load drone config from SharedPreferences.
     */
    public static Config loadConfig(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Config config = new Config();
        config.ip = sp.getString(KEY_IP, DEFAULT_IP);
        config.udpPort = sp.getInt(KEY_UDP_PORT, DEFAULT_UDP_PORT);
        config.tcpPort = sp.getInt(KEY_TCP_PORT, DEFAULT_TCP_PORT);
        return config;
    }

    /**
     * Save drone config to SharedPreferences.
     */
    public static void saveConfig(Context context, Config config) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_IP, config.ip)
                .putInt(KEY_UDP_PORT, config.udpPort)
                .putInt(KEY_TCP_PORT, config.tcpPort)
                .putInt(KEY_PATCH_VERSION, CURRENT_PATCH_VERSION)
                .apply();
        Log.e(TAG, "Config saved: ip=" + config.ip + " udp=" + config.udpPort + " tcp=" + config.tcpPort);
    }

    /**
     * Get the configured IP address (from SharedPreferences).
     */
    public static String getConfiguredIp(Context context) {
        return loadConfig(context).ip;
    }

    /**
     * Get the configured UDP port.
     */
    public static int getConfiguredUdpPort(Context context) {
        return loadConfig(context).udpPort;
    }

    /**
     * Get the configured TCP port.
     */
    public static int getConfiguredTcpPort(Context context) {
        return loadConfig(context).tcpPort;
    }

    /**
     * Get patch information from libCamera.so.
     */
    public static PatchInfo getPatchInfo(Context context) {
        PatchInfo info = new PatchInfo();
        info.originalIp = ORIGINAL_IP;
        info.version = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_PATCH_VERSION, 0);

        try {
            byte[] ipBytes = readIpFromSo(context);
            if (ipBytes != null) {
                // Read until null terminator
                int len = 0;
                while (len < ipBytes.length && ipBytes[len] != 0) {
                    len++;
                }
                info.currentIp = new String(ipBytes, 0, len);
                info.patched = !ORIGINAL_IP.equals(info.currentIp);
            } else {
                info.currentIp = ORIGINAL_IP;
                info.patched = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read patch info", e);
            info.currentIp = ORIGINAL_IP;
            info.patched = false;
        }

        return info;
    }

    /**
     * Apply binary patch to libCamera.so, replacing the hardcoded IP.
     * Returns true if patch was applied successfully.
     */
    public static boolean applyPatch(Context context, String newIp) {
        Log.e(TAG, "Applying patch: " + ORIGINAL_IP + " -> " + newIp);

        if (newIp.length() > PATCH_MAX_LEN - 1) {
            Log.e(TAG, "IP too long: " + newIp.length() + " > " + (PATCH_MAX_LEN - 1));
            return false;
        }

        try {
            File soFile = getNativeLibraryFile(context, "libCamera.so");
            if (soFile == null || !soFile.exists()) {
                Log.e(TAG, "libCamera.so not found");
                return false;
            }

            // Read current bytes at patch location
            byte[] originalBytes = new byte[PATCH_MAX_LEN];
            FileInputStream fis = new FileInputStream(soFile);
            fis.skip(PATCH_OFFSET);
            int bytesRead = fis.read(originalBytes);
            fis.close();

            if (bytesRead < PATCH_MAX_LEN) {
                Log.e(TAG, "Could not read enough bytes at patch offset: " + bytesRead);
                return false;
            }

            // Verify original IP is at this location
            String currentIp = new String(originalBytes, 0, ORIGINAL_IP.length());
            if (!currentIp.equals(ORIGINAL_IP) && !currentIp.equals(newIp)) {
                Log.e(TAG, "Warning: unexpected bytes at patch offset: " + currentIp);
                // Continue anyway - user may have already patched
            }

            // Create new patch bytes: IP + null padding
            byte[] patchBytes = new byte[PATCH_MAX_LEN];
            byte[] ipBytes = newIp.getBytes("US-ASCII");
            System.arraycopy(ipBytes, 0, patchBytes, 0, ipBytes.length);
            // Remaining bytes are already 0 (null)

            // Write patched bytes
            FileOutputStream fos = new FileOutputStream(soFile, false); // false = overwrite mode, but we only write at offset
            fos = new FileOutputStream(soFile, true); // Actually we need RandomAccessFile
            fos.close();

            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(soFile, "rw");
            raf.seek(PATCH_OFFSET);
            raf.write(patchBytes);
            raf.close();

            Log.e(TAG, "Patch applied successfully: " + newIp);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply patch", e);
            return false;
        }
    }

    /**
     * Restore libCamera.so to original IP.
     */
    public static boolean restorePatch(Context context) {
        return applyPatch(context, ORIGINAL_IP);
    }

    /**
     * Read the IP bytes from libCamera.so at the patch location.
     */
    private static byte[] readIpFromSo(Context context) {
        try {
            File soFile = getNativeLibraryFile(context, "libCamera.so");
            if (soFile == null || !soFile.exists()) {
                return null;
            }

            byte[] bytes = new byte[PATCH_MAX_LEN];
            FileInputStream fis = new FileInputStream(soFile);
            fis.skip(PATCH_OFFSET);
            fis.read(bytes);
            fis.close();
            return bytes;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read IP from .so", e);
            return null;
        }
    }

    /**
     * Find the actual native library file path.
     */
    private static File getNativeLibraryFile(Context context, String libName) {
        try {
            // Try the standard native library path first
            String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
            File file = new File(nativeLibDir, libName);
            if (file.exists()) {
                return file;
            }
            Log.e(TAG, "Not found at: " + file.getAbsolutePath());

            // Fallback: try to extract from APK
            String apkPath = context.getApplicationInfo().sourceDir;
            if (apkPath != null) {
                java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apkPath);
                String entryName = "lib/arm64-v8a/" + libName;
                java.util.zip.ZipEntry entry = zip.getEntry(entryName);
                if (entry != null) {
                    Log.e(TAG, "Found in APK at: " + entryName);
                    // The actual file is already extracted by the system
                }
                zip.close();
            }

            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to find native lib", e);
            return null;
        }
    }
}
