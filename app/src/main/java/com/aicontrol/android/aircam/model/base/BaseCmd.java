package com.aicontrol.android.aircam.model.base;

import android.util.Log;
import com.google.common.base.Ascii;
import com.aicontrol.android.aircam.utils.Camera;
import com.aicontrol.android.aircam.utils.Constants;
import com.aicontrol.android.aircam.utils.LogUtils;

/* loaded from: classes5.dex */
public class BaseCmd implements Runnable {
    public static final int RESUME = 0;
    public static final int START = 1;
    private byte[] cmdData = new byte[8];
    private byte[] snapData = new byte[8];
    private byte[] cmdNewData = new byte[20];
    private int sendType = 0;
    protected boolean bRunning = false;
    public byte powerVal = 0;
    public byte yawData = Byte.MIN_VALUE;
    public byte rollData = Byte.MIN_VALUE;
    public byte pitchData = Byte.MIN_VALUE;
    private int leftHVal = Constants.MAX_FINETUNE_VALUE + 1;
    private int rightHVal = Constants.MAX_FINETUNE_VALUE + 1;
    private int rightOVal = Constants.MAX_FINETUNE_VALUE + 1;
    private int lastLeftHVal = Constants.MAX_FINETUNE_VALUE + 1;
    private int lastRightHVal = Constants.MAX_FINETUNE_VALUE + 1;
    private int lastRightOVal = Constants.MAX_FINETUNE_VALUE + 1;
    private boolean isOneKeyFlyDown = false;
    private int isOneKeyFlyCount = 0;
    private boolean isOneKeyStopDown = false;
    private int isOneKeyStopCount = 0;
    private boolean isOneKeyMergencyDown = false;
    private int isOneKeyMergencyCount = 0;
    private boolean checkoutFlag = false;
    private int checkoutCount = 0;
    LogUtils logUtils = LogUtils.setLogger(BaseCmd.class);
    private Thread mDataThread = null;
    private int state = 0;
    private int resumeCount = 25;

    public BaseCmd() {
        IBaseCmd_Init();
        IBaseNewCmd_Init();
    }

    public void IBaseCmd_Init() {
        byte[] bArr = this.cmdData;
        bArr[0] = 102;
        bArr[1] = Byte.MIN_VALUE;
        bArr[2] = Byte.MIN_VALUE;
        bArr[3] = 0;
        bArr[4] = Byte.MIN_VALUE;
        bArr[5] = 0;
        bArr[6] = IBaseCmd_Odd();
        this.cmdData[7] = -103;
    }

    public void IBaseNewCmd_Init() {
        byte[] bArr = this.cmdNewData;
        bArr[0] = 102;
        bArr[1] = Ascii.DC4;
        bArr[2] = Byte.MIN_VALUE;
        bArr[3] = Byte.MIN_VALUE;
        bArr[4] = 0;
        bArr[5] = Byte.MIN_VALUE;
        bArr[6] = 0;
        bArr[7] = 0;
        bArr[8] = 0;
        bArr[9] = 0;
        bArr[10] = 0;
        bArr[11] = 0;
        bArr[12] = 0;
        bArr[13] = 0;
        bArr[14] = 0;
        bArr[15] = 0;
        bArr[16] = 0;
        bArr[17] = 0;
        bArr[18] = IBaseCmdNew_odd();
        this.cmdNewData[19] = -103;
    }

    public void ISnapCmd_Init() {
        byte[] bArr = this.snapData;
        bArr[0] = -86;
        bArr[1] = Byte.MIN_VALUE;
        bArr[2] = Byte.MIN_VALUE;
        bArr[3] = 0;
        bArr[4] = Byte.MIN_VALUE;
        bArr[5] = 0;
        bArr[6] = ISnapCmd_Odd();
        this.snapData[7] = 85;
    }

    private byte ISnapCmd_Odd() {
        byte b = this.snapData[1];
        for (int i = 2; i < 6; i++) {
            b = (byte) (b ^ this.snapData[i]);
        }
        return IBaseCmd_RightData((byte) (b & 255));
    }

    private int IBaseCmd_Byte2Int(byte b) {
        int i = 0;
        for (int i2 = 0; i2 < 8; i2++) {
            i += (int) (((b >> i2) & 1) * Math.pow(2.0d, i2));
        }
        return i;
    }

    private byte IBaseCmd_Odd() {
        byte b = this.cmdData[1];
        for (int i = 2; i < 6; i++) {
            b = (byte) (b ^ this.cmdData[i]);
        }
        return IBaseCmd_RightData((byte) (b & 255));
    }

    private byte IBaseCmdNew_odd() {
        byte b = this.cmdNewData[2];
        for (int i = 3; i < 18; i++) {
            b = (byte) (b ^ this.cmdNewData[i]);
        }
        return IBaseCmd_RightData((byte) (b & 255));
    }

    private byte IBaseCmd_RightData(byte b) {
        int IBaseCmd_Byte2Int = IBaseCmd_Byte2Int(b);
        if (IBaseCmd_Byte2Int == 102 || IBaseCmd_Byte2Int == 153) {
            IBaseCmd_Byte2Int++;
        }
        return (byte) IBaseCmd_Byte2Int;
    }

    public void takeOneKeyFly() {
        Log.e("[YXY]", "takeOneKeyFly: ");
        int i = this.sendType;
        if (i == 0 || i == 1) {
            byte[] bArr = this.cmdData;
            bArr[5] = (byte) (bArr[5] | 1);
            this.isOneKeyFlyCount = 0;
            this.isOneKeyFlyDown = true;
            return;
        }
        if (i == 2) {
            byte[] bArr2 = this.cmdNewData;
            bArr2[6] = (byte) (bArr2[6] | 1);
            this.isOneKeyFlyCount = 0;
            this.isOneKeyFlyDown = true;
        }
    }

    public void takOneKeyLand() {
        int i = this.sendType;
        if (i == 0 || i == 1) {
            byte[] bArr = this.cmdData;
            bArr[5] = (byte) (2 | bArr[5]);
            this.isOneKeyStopCount = 0;
            this.isOneKeyStopDown = true;
            return;
        }
        if (i == 2) {
            byte[] bArr2 = this.cmdNewData;
            bArr2[6] = (byte) (bArr2[6] | 1);
            this.isOneKeyStopCount = 0;
            this.isOneKeyStopDown = true;
        }
    }

    public void takeOneKeyMergency() {
        int i = this.sendType;
        if (i == 0 || i == 1) {
            byte[] bArr = this.cmdData;
            bArr[5] = (byte) (bArr[5] | 4);
            this.isOneKeyMergencyCount = 0;
            this.isOneKeyMergencyDown = true;
            return;
        }
        if (i == 2) {
            byte[] bArr2 = this.cmdNewData;
            bArr2[6] = (byte) (2 | bArr2[6]);
            this.isOneKeyMergencyCount = 0;
            this.isOneKeyMergencyDown = true;
        }
    }

    public void setCheckOutFlg() {
        int i = this.sendType;
        if (i == 0 || i == 1) {
            byte[] bArr = this.cmdData;
            bArr[5] = (byte) (bArr[5] | 128);
            this.checkoutCount = 0;
            this.checkoutFlag = true;
            return;
        }
        if (i == 2) {
            byte[] bArr2 = this.cmdNewData;
            bArr2[6] = (byte) (bArr2[6] | 4);
            this.checkoutCount = 0;
            this.checkoutFlag = true;
        }
    }

    public void setRotate(boolean z) {
        int i = this.sendType;
        if (i == 0 || i == 1) {
            if (z) {
                byte[] bArr = this.cmdData;
                bArr[5] = (byte) (bArr[5] | 8);
                return;
            } else {
                byte[] bArr2 = this.cmdData;
                bArr2[5] = (byte) (bArr2[5] & 247);
                return;
            }
        }
        if (i == 2) {
            if (z) {
                byte[] bArr3 = this.cmdNewData;
                bArr3[6] = (byte) (bArr3[6] | 8);
            } else {
                byte[] bArr4 = this.cmdNewData;
                bArr4[6] = (byte) (bArr4[6] & 247);
            }
        }
    }

    public void setNoHeadModle(boolean z) {
        int i = this.sendType;
        if (i == 0 || i == 1) {
            if (z) {
                byte[] bArr = this.cmdData;
                bArr[5] = (byte) (bArr[5] | 16);
                return;
            } else {
                byte[] bArr2 = this.cmdData;
                bArr2[5] = (byte) (bArr2[5] & 239);
                return;
            }
        }
        if (i == 2) {
            if (z) {
                byte[] bArr3 = this.cmdNewData;
                bArr3[7] = (byte) (1 | bArr3[7]);
            } else {
                byte[] bArr4 = this.cmdNewData;
                bArr4[7] = (byte) (bArr4[7] & 254);
            }
        }
    }

    public void setStayHigh(boolean z) {
        int i = this.sendType;
        if (i == 0 || i == 1 || i != 2) {
            return;
        }
        if (z) {
            byte[] bArr = this.cmdNewData;
            bArr[7] = (byte) (2 | bArr[7]);
        } else {
            byte[] bArr2 = this.cmdNewData;
            bArr2[7] = (byte) (bArr2[7] & 253);
        }
    }

    public void setTune(byte b, byte b2, byte b3) {
        if (b != -1) {
            this.leftHVal = b;
        } else if (b2 != -1) {
            this.rightHVal = b2;
        } else if (b3 != -1) {
            this.rightOVal = b3;
        }
    }

    public void clearCheckOutFlg() {
        int i = this.sendType;
        if (i == 0 || i == 1) {
            if (this.checkoutFlag) {
                int i2 = this.checkoutCount;
                if (i2 > 20) {
                    this.checkoutCount = 0;
                    byte[] bArr = this.cmdData;
                    bArr[5] = (byte) (bArr[5] & Byte.MAX_VALUE);
                    this.checkoutFlag = false;
                    return;
                }
                this.checkoutCount = i2 + 1;
                return;
            }
            return;
        }
        if (i == 2 && this.checkoutFlag) {
            int i3 = this.checkoutCount;
            if (i3 > 50) {
                this.checkoutCount = 0;
                byte[] bArr2 = this.cmdNewData;
                bArr2[6] = (byte) (bArr2[6] & 251);
                this.checkoutFlag = false;
                return;
            }
            this.checkoutCount = i3 + 1;
        }
    }

    public void clearOneKeyFly() {
        int i = this.sendType;
        if (i == 0 || i == 1) {
            if (this.isOneKeyFlyDown) {
                int i2 = this.isOneKeyFlyCount;
                if (i2 > 20) {
                    this.isOneKeyFlyCount = 0;
                    byte[] bArr = this.cmdData;
                    bArr[5] = (byte) (bArr[5] & 254);
                    this.isOneKeyFlyDown = false;
                    return;
                }
                this.isOneKeyFlyCount = i2 + 1;
                return;
            }
            return;
        }
        if (i == 2 && this.isOneKeyFlyDown) {
            int i3 = this.isOneKeyFlyCount;
            if (i3 > 50) {
                this.isOneKeyFlyCount = 0;
                byte[] bArr2 = this.cmdNewData;
                bArr2[6] = (byte) (bArr2[6] & 254);
                this.isOneKeyFlyDown = false;
                return;
            }
            this.isOneKeyFlyCount = i3 + 1;
        }
    }

    public void clearOneKeyStop() {
        int i = this.sendType;
        if (i == 0 || i == 1) {
            if (this.isOneKeyStopDown) {
                int i2 = this.isOneKeyStopCount;
                if (i2 > 20) {
                    this.isOneKeyStopCount = 0;
                    byte[] bArr = this.cmdData;
                    bArr[5] = (byte) (bArr[5] & 253);
                    this.isOneKeyStopDown = false;
                    return;
                }
                this.isOneKeyStopCount = i2 + 1;
                return;
            }
            return;
        }
        if (i == 2 && this.isOneKeyStopDown) {
            int i3 = this.isOneKeyStopCount;
            if (i3 > 50) {
                this.isOneKeyStopCount = 0;
                byte[] bArr2 = this.cmdNewData;
                bArr2[6] = (byte) (bArr2[6] & 254);
                this.isOneKeyStopDown = false;
                return;
            }
            this.isOneKeyStopCount = i3 + 1;
        }
    }

    public void clearOneKeyMergency() {
        int i = this.sendType;
        if (i == 0 || i == 1) {
            if (this.isOneKeyMergencyDown) {
                int i2 = this.isOneKeyMergencyCount;
                if (i2 > 20) {
                    this.isOneKeyMergencyCount = 0;
                    byte[] bArr = this.cmdData;
                    bArr[5] = (byte) (bArr[5] & 251);
                    this.isOneKeyMergencyDown = false;
                    return;
                }
                this.isOneKeyMergencyCount = i2 + 1;
                return;
            }
            return;
        }
        if (i == 2 && this.isOneKeyMergencyDown) {
            int i3 = this.isOneKeyMergencyCount;
            if (i3 > 50) {
                this.isOneKeyMergencyCount = 0;
                byte[] bArr2 = this.cmdNewData;
                bArr2[6] = (byte) (bArr2[6] & 253);
                this.isOneKeyMergencyDown = false;
                return;
            }
            this.isOneKeyMergencyCount = i3 + 1;
        }
    }

    public void onAccNotify(byte b, byte b2) {
        int i = this.sendType;
        if (i == 0 || i == 1) {
            byte[] bArr = this.cmdData;
            this.powerVal = b;
            bArr[3] = b;
            bArr[4] = dealWithYawValue(b2);
            return;
        }
        if (i == 2) {
            byte[] bArr2 = this.cmdNewData;
            this.powerVal = b;
            bArr2[4] = b;
            bArr2[5] = dealWithYawValue(b2);
        }
    }

    public void onDirNotify(byte b, byte b2) {
        int i = this.sendType;
        if (i == 0 || i == 1) {
            this.cmdData[1] = dealWithRollValue(b);
            this.cmdData[2] = dealWithPitchValue(b2);
        } else if (i == 2) {
            this.cmdNewData[2] = dealWithRollValue(b);
            this.cmdNewData[3] = dealWithPitchValue(b2);
        }
    }

    public byte dealWithYawValue(byte value) {
        int val = IBaseCmd_Byte2Int(value);
        int trim = this.leftHVal;
        int lastTrim = this.lastLeftHVal;
        int result;
        if (trim > lastTrim) {
            // trim increased: add delta
            result = val + (trim - lastTrim);
            if (result >= 255) {
                result = -1; // 0xFF as signed byte
            } else {
                result = (byte) result;
            }
        } else {
            // trim decreased: subtract delta
            int delta = lastTrim - trim;
            if (val > delta) {
                result = val - (byte) delta;
            } else {
                result = 0;
            }
        }
        return IBaseCmd_RightData((byte) result);
    }

    public byte dealWithRollValue(byte value) {
        int val = IBaseCmd_Byte2Int(value);
        int trim = this.rightHVal;
        int lastTrim = this.lastRightHVal;
        int result;
        if (trim > lastTrim) {
            // trim increased: add delta
            result = val + (trim - lastTrim);
            if (result >= 255) {
                result = -1; // 0xFF as signed byte
            } else {
                result = (byte) result;
            }
        } else {
            // trim decreased: subtract delta
            int delta = lastTrim - trim;
            if (val - delta > 0) {
                result = val - (byte) delta;
            } else {
                result = 0;
            }
        }
        return IBaseCmd_RightData((byte) result);
    }

    public byte dealWithPitchValue(byte b) {
        int i;
        byte b2;
        int IBaseCmd_Byte2Int = IBaseCmd_Byte2Int(b);
        int i2 = this.rightOVal;
        int i3 = this.lastRightOVal;
        if (i2 > i3) {
            i = IBaseCmd_Byte2Int + (i2 - i3);
            if (i >= 255) {
                b2 = -1;
            }
            b2 = (byte) i;
        } else {
            int i4 = i3 - i2;
            if (IBaseCmd_Byte2Int - i4 <= 0) {
                b2 = 0;
            } else {
                i = IBaseCmd_Byte2Int - ((byte) i4);
                b2 = (byte) i;
            }
        }
        return IBaseCmd_RightData(b2);
    }

    public void dealWithStart() {
        try {
            clearCheckOutFlg();
            clearOneKeyFly();
            clearOneKeyStop();
            clearOneKeyMergency();
            int i = this.sendType;
            if (i == 0 || i == 1) {
                Log.e("[YXY]", "dealWithStart: ");
                this.cmdData[6] = IBaseCmd_Odd();
                byte[] bArr = this.cmdData;
                Camera.iCmdSend(bArr, bArr.length);
                String Bytes2String = Bytes2String(this.cmdData);
                this.logUtils.e("YXY send data:" + Bytes2String);
                Thread.sleep(40L);
            } else if (i == 2) {
                this.cmdNewData[18] = IBaseCmdNew_odd();
                byte[] bArr2 = this.cmdNewData;
                Camera.iCmdSend(bArr2, bArr2.length);
                String Bytes2String2 = Bytes2String(this.cmdNewData);
                this.logUtils.e("YXY send data:" + Bytes2String2);
                Thread.sleep(20L);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void dealWithResume() {
        try {
            int i = this.resumeCount;
            if (i >= 25) {
                this.resumeCount = 0;
                byte[] bArr = this.snapData;
                Camera.iCmdSend(bArr, bArr.length);
                String Bytes2String = Bytes2String(this.snapData);
                this.logUtils.e("#### send data:" + Bytes2String);
            } else {
                this.resumeCount = i + 1;
            }
            Thread.sleep(30L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override // java.lang.Runnable
    public void run() {
        this.bRunning = true;
        while (this.bRunning) {
            this.bRunning = true;
            while (this.bRunning) {
                int i = this.state;
                if (i == 1) {
                    dealWithStart();
                } else if (i == 0) {
                    dealWithResume();
                }
            }
        }
    }

    private static String Bytes2String(byte[] bArr) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bArr) {
            sb.append(String.format("%02x  ", Byte.valueOf(b)));
        }
        return sb.toString();
    }

    public void start() {
        this.state = 1;
        Thread thread = this.mDataThread;
        if (thread == null || !thread.isAlive()) {
            this.mDataThread = new Thread(this);
            this.logUtils.e("start:  ");
            this.mDataThread.start();
        }
    }

    public void resume() {
        this.state = 0;
        this.isOneKeyFlyDown = false;
        this.isOneKeyStopDown = false;
        this.isOneKeyMergencyDown = false;
        this.checkoutFlag = false;
        ISnapCmd_Init();
        this.resumeCount = 25;
        Thread thread = this.mDataThread;
        if (thread == null || !thread.isAlive()) {
            this.mDataThread = new Thread(this);
            this.logUtils.e("start:  ");
            this.mDataThread.start();
        }
    }

    public void stop() {
        this.bRunning = false;
        this.logUtils.e("stop:  ");
        Thread thread = this.mDataThread;
        if (thread != null) {
            try {
                thread.join();
                this.mDataThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void setCameraType(int i) {
        this.sendType = i;
        Log.e("[YXY]", "getCameraType: " + i);
    }
}
