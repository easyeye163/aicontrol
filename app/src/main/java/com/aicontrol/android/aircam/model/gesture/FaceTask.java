package com.aicontrol.android.aircam.model.gesture;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import com.aicontrol.android.aircam.activity.PlayActivity;

/* loaded from: classes3.dex */
public class FaceTask extends AsyncTask {
    private Bitmap bitmap;
    private Handler handler = new Handler(new Handler.Callback() { // from class: com.hmx.recognition.FaceTask.1
        @Override // android.os.Handler.Callback
        public boolean handleMessage(Message message) {
            int i = message.what;
            if (i != 0) {
                if (i != 240) {
                    switch (i) {
                        case 253:
                            FaceTask.this.playActivity.resetHandRect();
                            break;
                        case 254:
                            FaceTask.this.playActivity.setHandPalm();
                            break;
                    }
                    return false;
                }
                FaceTask.this.playActivity.setHandFist();
                return false;
            }
            FaceTask.this.playActivity.setWaitFalse();
            return false;
        }
    });
    private OpenCVHelper openCVHelper = new OpenCVHelper(this.handler);
    private PlayActivity playActivity;

    public FaceTask(PlayActivity playActivity, Bitmap bitmap) {
        this.playActivity = playActivity;
        this.bitmap = bitmap;
    }

    @Override // android.os.AsyncTask
    protected Object doInBackground(Object[] objArr) {
        if (!this.playActivity.bAutoPhoto) {
            return null;
        }
        if (!PlayActivity.disposeFlagInit) {
            OpenCVHelper.initGesture(PlayActivity.path1, PlayActivity.path2);
            PlayActivity.disposeFlagInit = true;
        }
        if (this.bitmap.getWidth() % 2 != 0 || this.bitmap.getHeight() % 2 != 0) {
            int width = this.bitmap.getWidth();
            int height = this.bitmap.getHeight();
            if (width % 2 != 0) {
                width--;
            }
            if (height % 2 != 0) {
                height--;
            }
            this.bitmap = Bitmap.createBitmap(this.bitmap, 0, 0, width, height);
        }
        Bitmap bitmap = this.bitmap;
        if (OpenCVHelper.runGesture(bitmapToNv21(bitmap, bitmap.getWidth(), this.bitmap.getHeight()), this.bitmap.getWidth(), this.bitmap.getHeight(), 1.25f, 60, 1.25f, 35) >= 0) {
            return null;
        }
        Bitmap bitmap2 = this.bitmap;
        Matrix matrix = new Matrix();
        matrix.setScale(-1.0f, 1.0f);
        int width2 = bitmap2.getWidth();
        int height2 = bitmap2.getHeight();
        if (OpenCVHelper.runGesture(bitmapToNv21(Bitmap.createBitmap(bitmap2, 0, 0, width2, height2, matrix, true), width2, height2), width2, height2, 1.25f, 60, 1.25f, 35) >= 0) {
            return null;
        }
        this.handler.sendEmptyMessage(253);
        return null;
    }

    public static byte[] bitmapToNv21(Bitmap bitmap, int i, int i2) {
        if (bitmap == null || bitmap.getWidth() < i || bitmap.getHeight() < i2) {
            return null;
        }
        int[] iArr = new int[i * i2];
        bitmap.getPixels(iArr, 0, i, 0, 0, i, i2);
        return argbToNv21(iArr, i, i2);
    }

    private static byte[] argbToNv21(int[] iArr, int i, int i2) {
        int i3 = i * i2;
        int i4 = (i3 * 3) / 2;
        byte[] bArr = new byte[i4];
        int i5 = 0;
        int i6 = 0;
        for (int i7 = 0; i7 < i2; i7++) {
            int i8 = 0;
            while (i8 < i) {
                int i9 = iArr[i6];
                int i10 = (16711680 & i9) >> 16;
                int i11 = (65280 & i9) >> 8;
                int i12 = 255;
                int i13 = i9 & 255;
                int i14 = (((((i10 * 66) + (i11 * 129)) + (i13 * 25)) + 128) >> 8) + 16;
                int i15 = (((((i10 * (-38)) - (i11 * 74)) + (i13 * 112)) + 128) >> 8) + 128;
                int i16 = (((((i10 * 112) - (i11 * 94)) - (i13 * 18)) + 128) >> 8) + 128;
                int i17 = i5 + 1;
                if (i14 < 0) {
                    i14 = 0;
                } else if (i14 > 255) {
                    i14 = 255;
                }
                bArr[i5] = (byte) i14;
                if (i7 % 2 == 0 && i6 % 2 == 0 && i3 < i4 - 2) {
                    int i18 = i3 + 1;
                    if (i16 < 0) {
                        i16 = 0;
                    } else if (i16 > 255) {
                        i16 = 255;
                    }
                    bArr[i3] = (byte) i16;
                    i3 = i18 + 1;
                    if (i15 < 0) {
                        i12 = 0;
                    } else if (i15 <= 255) {
                        i12 = i15;
                    }
                    bArr[i18] = (byte) i12;
                }
                i6++;
                i8++;
                i5 = i17;
            }
        }
        return bArr;
    }
}
