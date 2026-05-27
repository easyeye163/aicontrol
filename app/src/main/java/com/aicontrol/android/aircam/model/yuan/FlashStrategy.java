package com.aicontrol.android.aircam.model.yuan;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;

/* loaded from: classes5.dex */
public interface FlashStrategy {
    void setCaptureRequest(CaptureRequest.Builder builder, CameraCaptureSession cameraCaptureSession, Handler handler);
}
