package com.aicontrol.android.aircam.model.listener;

import android.graphics.Bitmap;

/* loaded from: classes5.dex */
public interface INativeListener {
    void ICameraType(int i);

    void IWiFiConState(int i);

    void IWiFiRecvBmp(int i, int i2, Bitmap bitmap);

    void IWiFiSnapState(int i);

    void iFaceDector();
}
