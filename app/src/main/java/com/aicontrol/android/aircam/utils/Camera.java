package com.aicontrol.android.aircam.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Message;
import com.aicontrol.android.aircam.model.face.FaceDector;
import com.aicontrol.android.aircam.model.listener.INativeListener;
import com.aicontrol.android.aircam.model.yuan.IData;

public class Camera {
    private static final int CAMERA_TYPE = 3;
    private static final int WIFI_NOTIFY_PIC = 0;
    private static final int WIFI_NOTIFY_STATE = 1;
    private static final int WIFI_SNAP_STATE = 2;
    public static final int Y_TYPE_H264 = 1;
    public static final int Y_TYPE_H2654 = 2;
    public static final int Y_TYPE_MJPEG = 0;
    private static boolean bAutoClick = false;
    private static boolean bRecord = false;
    private static Context mContext;
    static Handler mHandler;
    private static IData mListener;
    private static byte[] mlock;
    private static INativeListener nativeListener;
    private static BitmapFactory.Options options = new BitmapFactory.Options();
    static LogUtils logUtils = LogUtils.setLogger(Camera.class);
    private static long nStartFrameTime = 0;
    private static FaceDector faceDector = null;
    private static int rotateAngle = 0;
    private static int resolution = 0;
    private static int iretain = 0;

    // === Native method bridges - delegate to com.tzh.wifi.utils.Camera ===
    public static void iCameraCloseFile() { com.tzh.wifi.utils.Camera.iCameraCloseFile(); }
    public static void iCameraDeinit() { com.tzh.wifi.utils.Camera.iCameraDeinit(); }
    public static int iCameraEncodeStart(String str, int i) { return com.tzh.wifi.utils.Camera.iCameraEncodeStart(str, i); }
    public static int iCameraEncodeStop() { return com.tzh.wifi.utils.Camera.iCameraEncodeStop(); }
    public static byte[] iCameraGetOneFrame(int i) { return com.tzh.wifi.utils.Camera.iCameraGetOneFrame(i); }
    public static byte[] iCameraGetOneSecond(double d) { return com.tzh.wifi.utils.Camera.iCameraGetOneSecond(d); }
    public static int iCameraGetTotalFrame() { return com.tzh.wifi.utils.Camera.iCameraGetTotalFrame(); }
    public static double iCameraGetTotalTime() { return com.tzh.wifi.utils.Camera.iCameraGetTotalTime(); }
    public static int iCameraInit() { return com.tzh.wifi.utils.Camera.iCameraInit(); }
    public static void iCameraOpenFile(String str) { com.tzh.wifi.utils.Camera.iCameraOpenFile(str); }
    public static int iCameraRecSetParams(int i, int i2, int i3) { return com.tzh.wifi.utils.Camera.iCameraRecSetParams(i, i2, i3); }
    public static int iCameraRecStart(String str) { return com.tzh.wifi.utils.Camera.iCameraRecStart(str); }
    public static void iCameraRecStop() { com.tzh.wifi.utils.Camera.iCameraRecStop(); }
    public static int iCameraRecWrite(byte[] bArr, int i) { return com.tzh.wifi.utils.Camera.iCameraRecWrite(bArr, i); }
    public static int iCameraRoate() { return com.tzh.wifi.utils.Camera.iCameraRoate(); }
    public static int iCameraSetMode(int i) { return com.tzh.wifi.utils.Camera.iCameraSetMode(i); }
    public static int iCameraStart() { return com.tzh.wifi.utils.Camera.iCameraStart(); }
    public static void iCameraStop() { com.tzh.wifi.utils.Camera.iCameraStop(); }
    public static int iCameraSwitch() { return com.tzh.wifi.utils.Camera.iCameraSwitch(); }
    public static int iCameraWritePic(byte[] bArr, int i) { return com.tzh.wifi.utils.Camera.iCameraWritePic(bArr, i); }
    public static void iCmdResume() { com.tzh.wifi.utils.Camera.iCmdResume(); }
    public static int iCmdSend(byte[] bArr, int i) { return com.tzh.wifi.utils.Camera.iCmdSend(bArr, i); }
    public static int iCmdStart() { return com.tzh.wifi.utils.Camera.iCmdStart(); }
    public static void iCmdStop() { com.tzh.wifi.utils.Camera.iCmdStop(); }
    public static int iYuanInit(int i, int i2, int i3, int i4, int i5, int i6, int i7, String str, String str2, int i8, int i9) { return com.tzh.wifi.utils.Camera.iYuanInit(i, i2, i3, i4, i5, i6, i7, str, str2, i8, i9); }
    public static int iYuanProc(byte[] bArr, int i, int i2) { return com.tzh.wifi.utils.Camera.iYuanProc(bArr, i, i2); }
    public static int iYuanRelease() { return com.tzh.wifi.utils.Camera.iYuanRelease(); }
    public static int isEncodingVadio() { return com.tzh.wifi.utils.Camera.isEncodingVadio(); }
    public static void stop_music() { com.tzh.wifi.utils.Camera.stop_music(); }

    static {
        try {
            // Force-load the JNI bridge class which loads all native libraries
            Class.forName("com.tzh.wifi.utils.Camera");
            logUtils.e("=== JNI bridge loaded OK ===");
        } catch (Throwable t) {
            logUtils.e("=== JNI bridge load FAILED ===");
            t.printStackTrace();
        }
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                super.handleMessage(message);
                int i = message.arg1;
                if (i == 0) {
                    if (Camera.nativeListener != null) {
                        Camera.nativeListener.IWiFiRecvBmp(Camera.resolution, Camera.iretain, (Bitmap) message.obj);
                    }
                } else if (i == 1) {
                    if (Camera.nativeListener != null) {
                        Camera.nativeListener.IWiFiConState(((Integer) message.obj).intValue());
                    }
                } else {
                    if (i != 2) {
                        if (i == 3 && Camera.nativeListener != null) {
                            Camera.nativeListener.ICameraType(((Integer) message.obj).intValue());
                            return;
                        }
                        return;
                    }
                    if (Camera.nativeListener != null) {
                        Camera.nativeListener.IWiFiSnapState(((Integer) message.obj).intValue());
                    }
                }
            }
        };
        mlock = new byte[0];
    }

    public void onAutoPhotoClick(boolean z, int i) {
        bAutoClick = z;
        rotateAngle = i;
    }

    public static void sendMessage(int i, Object obj) {
        if (mHandler != null) {
            Message obtain = Message.obtain();
            obtain.arg1 = i;
            obtain.obj = obj;
            mHandler.sendMessage(obtain);
        }
    }

    public Camera(Context context, INativeListener iNativeListener) {
        mContext = context;
        nativeListener = iNativeListener;
        faceDector = new FaceDector(iNativeListener);
    }

    public void startRecord() {
        bRecord = true;
        logUtils.e("startRecord");
    }

    public void stopRecord() {
        bRecord = false;
        logUtils.e("stopRecord");
        iCameraRecStop();
    }

    public static void OnImageRecv(int i, byte[] bArr, int i2, int i3) {
        Bitmap decodeByteArray;
        System.currentTimeMillis();
        resolution = i;
        iretain = i3;
        synchronized (mlock) {
            IData iData = mListener;
            if (iData != null) {
                iData.onData(bArr, i2);
            }
        }
        if (i3 == 1) {
            BitmapFactory.Options options2 = new BitmapFactory.Options();
            options2.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bArr, 0, i2, options2);
            int i4 = options2.outWidth;
            int i5 = options2.outHeight;
            Matrix matrix = new Matrix();
            matrix.postRotate(270.0f);
            decodeByteArray = Bitmap.createBitmap(BitmapFactory.decodeByteArray(bArr, 0, i2), 0, 0, i4, i5, matrix, true);
        } else {
            decodeByteArray = BitmapFactory.decodeByteArray(bArr, 0, i2);
        }
        if (decodeByteArray != null) {
            sendMessage(0, decodeByteArray);
        }
    }

    public static void OnWiFiStateChange(int i) {
        sendMessage(1, Integer.valueOf(i));
    }

    public static void OnCameraType(int i) {
        sendMessage(3, Integer.valueOf(i));
    }

    public static void onSnapRecClick(int i) {
        sendMessage(2, Integer.valueOf(i));
    }

    public void setOnDatListener(IData iData) {
        synchronized (mlock) {
            mListener = iData;
        }
    }
}
