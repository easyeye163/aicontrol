package com.aicontrol.android.aircam.view.pathdraw;

import android.animation.TypeEvaluator;

/* loaded from: classes5.dex */
public class PathEvaluator implements TypeEvaluator<PathPoint> {
    @Override // android.animation.TypeEvaluator
    public PathPoint evaluate(float f, PathPoint pathPoint, PathPoint pathPoint2) {
        float f2;
        float f3;
        if (pathPoint2.mAction == 1) {
            f2 = pathPoint.pointX + ((pathPoint2.pointX - pathPoint.pointX) * f);
            f3 = pathPoint.pointY + (f * (pathPoint2.pointY - pathPoint.pointY));
        } else {
            f2 = pathPoint2.pointX;
            f3 = pathPoint2.pointY;
        }
        return PathPoint.moveTo((int) f2, (int) f3);
    }
}
