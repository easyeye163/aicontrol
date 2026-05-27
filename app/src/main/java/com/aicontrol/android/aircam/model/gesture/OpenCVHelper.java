package com.aicontrol.android.aircam.model.gesture;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

/* loaded from: classes3.dex */
public class OpenCVHelper {
    public static int MSG_FISHRECT = 255;
    public static int MSG_PALMRECT = 254;
    public static int MSG_WAITFALSE;
    private static Handler handler;
    private static long nStartFistTime;
    private static long nStartPalmTime;

    public static native int close();

    public static native int[] gray(int[] iArr, int i, int i2);

    public static native int initGesture(String str, String str2);

    public static native int runGesture(byte[] bArr, int i, int i2, float f, int i3, float f2, int i4);

    public OpenCVHelper(Handler handler2) {
        handler = handler2;
    }

    private void setHandler(Handler handler2) {
        handler = handler2;
    }

    static {
        System.loadLibrary("opencv_java3");
    }

    public static void fistRect(int[] iArr) {
        Log.e("OpenCVHelper", "手势：fistRect");
        long currentTimeMillis = System.currentTimeMillis();
        Message message = new Message();
        long j = nStartFistTime;
        if (j == 0) {
            nStartFistTime = System.currentTimeMillis();
            message.what = MSG_WAITFALSE;
            handler.sendMessage(message);
        } else {
            if (currentTimeMillis - j < 1500) {
                message.what = MSG_FISHRECT;
            } else {
                message.what = MSG_WAITFALSE;
            }
            handler.sendMessage(message);
            nStartFistTime = currentTimeMillis;
        }
    }

    public static void palmRect(int[] iArr) {
        Log.e("OpenCVHelper", "手势：palmRect");
        long currentTimeMillis = System.currentTimeMillis();
        Message message = new Message();
        long j = nStartPalmTime;
        if (j == 0) {
            nStartPalmTime = System.currentTimeMillis();
            message.what = MSG_WAITFALSE;
            handler.sendMessage(message);
        } else {
            if (currentTimeMillis - j < 1500) {
                message.what = MSG_PALMRECT;
            } else {
                message.what = MSG_WAITFALSE;
            }
            handler.sendMessage(message);
            nStartPalmTime = currentTimeMillis;
        }
    }
}
