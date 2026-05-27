package com.aicontrol.android.aircam.utils;

import android.content.Context;
import android.util.TypedValue;

/* loaded from: classes5.dex */
public class PxUtils {
    public static int dpToPx(Context context, int i) {
        return (int) TypedValue.applyDimension(1, i, context.getApplicationContext().getResources().getDisplayMetrics());
    }

    public static int pxToDp(Context context, int i) {
        return (int) ((i / context.getApplicationContext().getResources().getDisplayMetrics().density) + 0.5f);
    }

    public static int getDeviceWidthInPixel(Context context) {
        return context.getApplicationContext().getResources().getDisplayMetrics().widthPixels;
    }

    public static int getDeviceHeightInPixel(Context context) {
        return context.getApplicationContext().getResources().getDisplayMetrics().heightPixels;
    }
}
