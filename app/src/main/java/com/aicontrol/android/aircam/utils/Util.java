package com.aicontrol.android.aircam.utils;

import android.graphics.Point;

/* loaded from: classes5.dex */
public class Util {
    public static int Length(int i, int i2, int i3, int i4) {
        return (int) Math.sqrt(Math.pow(Math.abs(i - i3), 2.0d) + Math.pow(Math.abs(i2 - i4), 2.0d));
    }

    public static double getRadian(int i, int i2, int i3, int i4) {
        double d = i3 - i;
        return Math.acos(d / Math.sqrt(Math.pow(d, 2.0d) + Math.pow(i4 - i2, 2.0d))) * (i4 < i2 ? -1 : 1);
    }

    public static Point UpdatePoint(int i, int i2, int i3, int i4, int i5) {
        Point point = new Point();
        double radian = getRadian(i, i2, i3, i4);
        double d = i5;
        point.x = (int) Math.abs(i3 - (Math.cos(radian) * d));
        point.y = (int) Math.abs(i4 - (d * Math.sin(radian)));
        return point;
    }

    public static int GetUpDown(int i, int i2, int i3, int i4) {
        return (i4 * Math.abs(i - i3)) / i2;
    }

    public static int GetLR(int i, int i2, int i3, int i4) {
        return (i4 * Math.abs(i - i3)) / i2;
    }
}
