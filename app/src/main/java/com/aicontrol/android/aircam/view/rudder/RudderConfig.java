package com.aicontrol.android.aircam.view.rudder;

import com.aicontrol.android.R;

/* loaded from: classes5.dex */
public class RudderConfig {
    public int thumbResId = R.mipmap.play_direct_small_icon;
    public int bgResId = R.mipmap.play_direct_large_icon;
    public int thumbSizeLandRes = R.dimen.y170;
    public int thumbSizePortRes = R.dimen.y136;
    public int bgSizeLandRes = R.dimen.y352;
    public int bgSizePortRes = R.dimen.y282;
    public int vOffsetLandRes = R.dimen.y40;
    public int vOffsetPortRes = R.dimen.y300;
    public int leftPaintColor = -3355444;
    public int rightPaintColor = -65536;
    public int strokeWidthRes = R.dimen.y10;
    public float smoothing = 0.3f;
    public float pressScale = 1.0f;

    public static RudderConfig getDefault() {
        return new RudderConfig();
    }
}
