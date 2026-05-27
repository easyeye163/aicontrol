package com.aicontrol.android.aircam.view.pathdraw;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/* loaded from: classes5.dex */
public class AnimationPath {
    private List<PathPoint> mPonters = new ArrayList();
    private float lineLen = 0.0f;

    public void moveTo(int i, int i2) {
        this.mPonters.add(PathPoint.moveTo(i, i2));
    }

    public PathPoint getStart() {
        if (this.mPonters.size() == 0) {
            return null;
        }
        return this.mPonters.get(0);
    }

    public void lineTo(int i, int i2) {
        this.lineLen = (float) (this.lineLen + Math.sqrt(Math.pow(i, 2.0d) + Math.pow(i2, 2.0d)));
        this.mPonters.add(PathPoint.lineTo(i, i2));
    }

    public void clear() {
        this.lineLen = 0.0f;
        this.mPonters.clear();
    }

    public List<PathPoint> getLPoints() {
        return this.mPonters;
    }

    public float getPathLen() {
        return this.lineLen;
    }

    public Collection<PathPoint> getPoints() {
        return this.mPonters;
    }

    public PathPoint getEndPoint() {
        if (this.mPonters.size() == 0) {
            return null;
        }
        return this.mPonters.get(this.mPonters.size() - 1);
    }
}
