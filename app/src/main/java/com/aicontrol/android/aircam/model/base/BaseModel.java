package com.aicontrol.android.aircam.model.base;

import com.aicontrol.android.aircam.utils.Camera;

/* loaded from: classes5.dex */
public class BaseModel {
    public void ICmd_AccNotify(byte b, byte b2) {
    }

    public void ICmd_CheckOutFlg() {
    }

    public void ICmd_DirNotify(byte b, byte b2) {
    }

    public void ICmd_NoHeadModle(boolean z) {
    }

    public void ICmd_OneKeyFly() {
    }

    public void ICmd_OneKeyLand() {
    }

    public void ICmd_OneKeyMergency() {
    }

    public void ICmd_Resume() {
    }

    public void ICmd_SetRotate(boolean z) {
    }

    public void ICmd_SetTune(byte b, byte b2, byte b3) {
    }

    public void ICmd_Start() {
    }

    public void ICmd_StayHighModle(boolean z) {
    }

    public void ICmd_Stop() {
    }

    public void Recording(boolean z) {
    }

    public int iCameraRoate(int i) {
        return 0;
    }

    public void takeSnap() {
    }

    public int iCameraInit() {
        return Camera.iCameraInit();
    }

    public void iCameraDeinit() {
        Camera.iCameraDeinit();
    }

    public int iCameraStart() {
        return Camera.iCameraStart();
    }

    public int iCameraSetMode(int i) {
        return Camera.iCameraSetMode(i);
    }

    public void iCameraStop() {
        Camera.iCameraStop();
    }

    public int iCameraRecStart(String str) {
        return Camera.iCameraRecStart(str);
    }

    public void iCameraRecStop() {
        Camera.iCameraRecStop();
    }

    public int iCameraRecWrite(byte[] bArr, int i) {
        return Camera.iCameraRecWrite(bArr, i);
    }

    public int iCameraWritePic(byte[] bArr, int i) {
        return Camera.iCameraWritePic(bArr, i);
    }

    public int iCameraRecSetParams(int i, int i2, int i3) {
        return Camera.iCameraRecSetParams(i, i2, i3);
    }

    public int iCmdStart() {
        return Camera.iCmdStart();
    }

    public int iCmdSend(byte[] bArr, int i) {
        return Camera.iCmdSend(bArr, i);
    }

    public void iCmdStop() {
        Camera.iCmdStop();
    }

    public int iCameraEncodeStart(String str, int i) {
        return Camera.iCameraEncodeStart(str, i);
    }

    public int iCameraEncodeStop() {
        return Camera.iCameraEncodeStop();
    }

    public int isEncodingVadio() {
        return Camera.isEncodingVadio();
    }
}
