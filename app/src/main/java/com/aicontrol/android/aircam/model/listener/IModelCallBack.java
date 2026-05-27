package com.aicontrol.android.aircam.model.listener;

import android.graphics.Bitmap;

/* loaded from: classes5.dex */
public interface IModelCallBack {
    void cameraType(int i);

    void connected();

    void disconnected();

    void iFaceDector();

    void recvFrame(int i, int i2, Bitmap bitmap);

    void snapState(int i);
}
