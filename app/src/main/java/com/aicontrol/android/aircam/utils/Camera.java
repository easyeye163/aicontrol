package com.aicontrol.android.aircam.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Message;
import com.aicontrol.android.aircam.model.face.FaceDector;
import com.aicontrol.android.aircam.model.listener.INativeListener;
import com.aicontrol.android.aircam.utils.LogUtils;
import com.aicontrol.android.aircam.model.yuan.IData;

/* loaded from: classes5.dex */
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

    public static final native int iCameraRoate();

    public static final native int iCameraSetMode(int i);

    public static final native int iCameraStart();

    public static final native void iCameraStop();

    public static final native int iCameraSwitch();

    public static final native int iCameraWritePic(byte[] bArr, int i);

    public static final native void iCmdResume();

    public static final native int iCmdSend(byte[] bArr, int i);

    public static final native int iCmdStart();

    public static final native void iCmdStop();

    public static native int iYuanInit(int i, int i2, int i3, int i4, int i5, int i6, int i7, String str, String str2, int i8, int i9);

    public static native int iYuanProc(byte[] bArr, int i, int i2);

    public static native int iYuanRelease();

    public static final native int isEncodingVadio();

    public static native void stop_music();

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
        mHandler = new Handler() { // from class: com.tzh.wifi.utils.Camera.1
            @Override // android.os.Handler
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
