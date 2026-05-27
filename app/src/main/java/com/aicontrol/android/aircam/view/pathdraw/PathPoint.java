package com.aicontrol.android.aircam.view.pathdraw;

/* loaded from: classes5.dex */
public class PathPoint {
    public static final int LINE = 1;
    public static final int POINT = 0;
    public static final int SECOND_CURVE = 2;
    public static final int THIRD_CURVE = 3;
    public int ctrlX;
    public int ctrlY;
    public int mAction;
    public int pointX;
    public int pointY;

    private PathPoint(int i, int i2, int i3) {
        this.mAction = i;
        this.pointX = i2;
        this.pointY = i3;
    }

    public static PathPoint moveTo(int i, int i2) {
        return new PathPoint(0, i, i2);
    }

    public static PathPoint lineTo(int i, int i2) {
        return new PathPoint(1, i, i2);
    }
}
