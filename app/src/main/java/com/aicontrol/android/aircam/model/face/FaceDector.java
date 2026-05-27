package com.aicontrol.android.aircam.model.face;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.FaceDetector;
import android.os.AsyncTask;
import com.aicontrol.android.aircam.model.listener.INativeListener;
import com.aicontrol.android.aircam.utils.LogUtils;
import java.util.Arrays;

/* loaded from: classes5.dex */
public class FaceDector {
    private INativeListener cameraListener;
    private FaceDetector.Face[] mFaces;
    private int maxFace = 5;
    private FaceDetector mFaceDetecotr = null;
    private FaceAysncTask faceAysncTask = null;
    LogUtils logEx = LogUtils.setLogger(FaceDector.class);
    boolean bFaceDector = false;
    BitmapFactory.Options bmpOptions = new BitmapFactory.Options();
    byte[] picData = new byte[409600];
    int piclength = 0;
    private int rotateAngle = 0;
    private Matrix matrix = new Matrix();

    public FaceDector(INativeListener iNativeListener) {
        this.cameraListener = null;
        this.bmpOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        this.cameraListener = iNativeListener;
    }

    public void onFaceDector(byte[] bArr, int i, int i2) {
        if (this.bFaceDector) {
            return;
        }
        this.bFaceDector = true;
        Arrays.fill(this.picData, (byte) 0);
        System.arraycopy(bArr, 0, this.picData, 0, i);
        this.piclength = i;
        this.rotateAngle = i2;
        this.matrix.setRotate(i2);
        new FaceAysncTask().execute(new Void[0]);
    }

    public class FaceAysncTask extends AsyncTask<Void, Integer, Integer> {
        public FaceAysncTask() {
        }

        @Override // android.os.AsyncTask
        protected void onPreExecute() {
            super.onPreExecute();
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(Integer num) {
            super.onPostExecute(num);
            if (num.intValue() != 0 && FaceDector.this.cameraListener != null) {
                FaceDector.this.cameraListener.iFaceDector();
            }
            FaceDector.this.bFaceDector = false;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public Integer doInBackground(Void... voidArr) {
            int i = 0;
            Bitmap decodeByteArray = BitmapFactory.decodeByteArray(FaceDector.this.picData, 0, FaceDector.this.piclength, FaceDector.this.bmpOptions);
            if (decodeByteArray != null) {
                Bitmap createBitmap = Bitmap.createBitmap(decodeByteArray, 0, 0, decodeByteArray.getWidth(), decodeByteArray.getHeight(), FaceDector.this.matrix, false);
                FaceDector.this.mFaceDetecotr = new FaceDetector(createBitmap.getWidth(), createBitmap.getHeight(), 2);
                FaceDector.this.mFaces = new FaceDetector.Face[2];
                i = FaceDector.this.mFaceDetecotr.findFaces(createBitmap, FaceDector.this.mFaces);
                FaceDector.this.logEx.e("face:" + i);
            } else {
                FaceDector.this.logEx.e("bitmap is null!");
            }
            return Integer.valueOf(i);
        }
    }
}
