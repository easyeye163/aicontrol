package com.aicontrol.android.aircam.model;

import android.content.Context;
import android.graphics.Bitmap;
import com.aicontrol.android.aircam.utils.Camera;
import com.aicontrol.android.aircam.model.base.BaseCmd;
import com.aicontrol.android.aircam.model.base.BaseModel;
import com.aicontrol.android.aircam.model.listener.IModelCallBack;
import com.aicontrol.android.aircam.model.listener.INativeListener;
import com.aicontrol.android.aircam.utils.ConfigUtils;
import com.aicontrol.android.aircam.model.yuan.IData;

/* loaded from: classes5.dex */
public class WiFiModelImpl extends BaseModel implements INativeListener {
    private BaseCmd baseCmd;
    private Camera mCamera;
    private Context mContext;
    private IModelCallBack modelCallBack;

    public WiFiModelImpl(Context context, IModelCallBack iModelCallBack) {
        this.mCamera = null;
        this.baseCmd = null;
        this.mContext = context;
        this.modelCallBack = iModelCallBack;
        this.mCamera = new Camera(context, this);
        BaseCmd baseCmd = new BaseCmd();
        this.baseCmd = baseCmd;
        baseCmd.setTune((byte) ConfigUtils.getLeftTune(context), (byte) ConfigUtils.getRightTune(context), (byte) ConfigUtils.getCenterTune(context));
    }

    @Override // com.tzh.wifi.wificam.model.listener.INativeListener
    public void iFaceDector() {
        IModelCallBack iModelCallBack = this.modelCallBack;
        if (iModelCallBack != null) {
            iModelCallBack.iFaceDector();
        }
    }

    public void startRecord() {
        Camera camera = this.mCamera;
        if (camera != null) {
            camera.startRecord();
        }
    }

    public void stopRecord() {
        Camera camera = this.mCamera;
        if (camera != null) {
            camera.stopRecord();
        }
    }

    @Override // com.tzh.wifi.wificam.model.listener.INativeListener
    public void IWiFiConState(int i) {
        IModelCallBack iModelCallBack = this.modelCallBack;
        if (iModelCallBack != null) {
            if (i == 0) {
                iModelCallBack.disconnected();
            } else if (i == 1) {
                iModelCallBack.connected();
            }
        }
    }

    @Override // com.tzh.wifi.wificam.model.listener.INativeListener
    public void ICameraType(int i) {
        this.baseCmd.setCameraType(i);
        IModelCallBack iModelCallBack = this.modelCallBack;
        if (iModelCallBack != null) {
            iModelCallBack.cameraType(i);
        }
    }

    @Override // com.tzh.wifi.wificam.model.listener.INativeListener
    public void IWiFiSnapState(int i) {
        IModelCallBack iModelCallBack = this.modelCallBack;
        if (iModelCallBack != null) {
            iModelCallBack.snapState(i);
        }
    }

    @Override // com.tzh.wifi.wificam.model.listener.INativeListener
    public void IWiFiRecvBmp(int i, int i2, Bitmap bitmap) {
        IModelCallBack iModelCallBack = this.modelCallBack;
        if (iModelCallBack != null) {
            iModelCallBack.recvFrame(i, i2, bitmap);
        }
    }

    public void onAutoPhotoClick(boolean z, int i) {
        Camera camera = this.mCamera;
        if (camera != null) {
            camera.onAutoPhotoClick(z, i);
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_AccNotify(byte b, byte b2) {
        super.ICmd_AccNotify(b, b2);
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.onAccNotify(b, b2);
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_CheckOutFlg() {
        super.ICmd_CheckOutFlg();
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.setCheckOutFlg();
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_DirNotify(byte b, byte b2) {
        super.ICmd_DirNotify(b, b2);
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.onDirNotify(b, b2);
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_NoHeadModle(boolean z) {
        super.ICmd_NoHeadModle(z);
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.setNoHeadModle(z);
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_StayHighModle(boolean z) {
        super.ICmd_StayHighModle(z);
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.setStayHigh(z);
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_OneKeyFly() {
        super.ICmd_OneKeyFly();
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.takeOneKeyFly();
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_OneKeyLand() {
        super.ICmd_OneKeyLand();
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.takOneKeyLand();
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_OneKeyMergency() {
        super.ICmd_OneKeyMergency();
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.takeOneKeyMergency();
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_SetRotate(boolean z) {
        super.ICmd_SetRotate(z);
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.setRotate(z);
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_SetTune(byte b, byte b2, byte b3) {
        super.ICmd_SetTune(b, b2, b3);
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.setTune(b, b2, b3);
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_Start() {
        super.ICmd_Start();
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.start();
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_Resume() {
        super.ICmd_Resume();
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.resume();
        }
    }

    @Override // com.tzh.wifi.wificam.model.base.BaseModel
    public void ICmd_Stop() {
        super.ICmd_Stop();
        BaseCmd baseCmd = this.baseCmd;
        if (baseCmd != null) {
            baseCmd.stop();
        }
    }

    public void setOnDataListener(IData iData) {
        this.mCamera.setOnDatListener(iData);
    }
}
