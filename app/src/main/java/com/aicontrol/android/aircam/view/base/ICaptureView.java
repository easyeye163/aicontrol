package com.aicontrol.android.aircam.view.base;

import android.graphics.Bitmap;

/* loaded from: classes5.dex */
public interface ICaptureView {
    void cameraType(int i);

    void connected();

    void disconnected();

    void onFaceDector();

    void reciveBitmap(int i, int i2, Bitmap bitmap);

    void snapState(int i);
}
