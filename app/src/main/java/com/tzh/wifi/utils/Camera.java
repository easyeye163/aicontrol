package com.tzh.wifi.utils;

/**
 * JNI bridge class - matches the native class path expected by libCamera.so.
 * The .so registers all native methods to "com/tzh/wifi/utils/Camera" in JNI_OnLoad.
 * This class is just a thin wrapper; the real implementation is in
 * com.aicontrol.android.aircam.utils.Camera.
 */
public class Camera {

    static {
        System.loadLibrary("Camera");
        System.loadLibrary("yuv");
        System.loadLibrary("jpeg");
        System.loadLibrary("turbojpeg");
        System.loadLibrary("avcodec");
        System.loadLibrary("avfilter");
        System.loadLibrary("avformat");
        System.loadLibrary("avutil");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
        System.loadLibrary("avdevice");
        System.loadLibrary("c++_shared");
    }

    // Native methods - match the signatures expected by libCamera.so JNI_OnLoad
    public static native void iCameraCloseFile();
    public static final native void iCameraDeinit();
    public static final native int iCameraEncodeStart(String str, int i);
    public static final native int iCameraEncodeStop();
    public static native byte[] iCameraGetOneFrame(int i);
    public static native byte[] iCameraGetOneSecond(double d);
    public static native int iCameraGetTotalFrame();
    public static native double iCameraGetTotalTime();
    public static final native int iCameraInit();
    public static native void iCameraOpenFile(String str);
    public static final native int iCameraRecSetParams(int i, int i2, int i3);
    public static final native int iCameraRecStart(String str);
    public static final native void iCameraRecStop();
    public static final native int iCameraRecWrite(byte[] bArr, int i);
    public static native int iCameraRoate();
    public static native int iCameraSetMode(int i);
    public static native int iCameraStart();
    public static native void iCameraStop();
    public static native int iCameraSwitch();
    public static native int iCameraWritePic(byte[] bArr, int i);
    public static native void iCmdResume();
    public static native int iCmdSend(byte[] bArr, int i);
    public static native int iCmdStart();
    public static native void iCmdStop();
    public static native int iYuanInit(int i, int i2, int i3, int i4, int i5, int i6, int i7, String str, String str2, int i8, int i9);
    public static native int iYuanProc(byte[] bArr, int i, int i2);
    public static native int iYuanRelease();
    public static final native int isEncodingVadio();
    public static native void stop_music();

    // JNI callbacks from native code
    public static void OnImageRecv(int i, byte[] bArr, int i2, int i3) {
        com.aicontrol.android.aircam.utils.Camera.OnImageRecv(i, bArr, i2, i3);
    }

    public static void OnWiFiStateChange(int i) {
        com.aicontrol.android.aircam.utils.Camera.OnWiFiStateChange(i);
    }

    public static void OnCameraType(int i) {
        com.aicontrol.android.aircam.utils.Camera.OnCameraType(i);
    }

    public static void onSnapRecClick(int i) {
        com.aicontrol.android.aircam.utils.Camera.onSnapRecClick(i);
    }
}
