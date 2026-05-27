package com.aicontrol.android.aircam.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.widget.Toast;
import com.aicontrol.android.aircam.presenter.listener.ITargetBitmapFixListener;
import java.util.ArrayList;
import java.util.List;

/* loaded from: classes5.dex */
public class BmpUtils implements Runnable {
    public static final int IMAGE_MAX_SCALE_SIZE = 50;
    public static final int IMAGE_MIN_SCALE_SIZE = 1;
    private int imgHeight;
    private int imgWidth;
    private Context mContext;
    private ITargetBitmapFixListener onBitmapFixListener;
    LogUtils logUtils = LogUtils.setLogger(BmpUtils.class);
    public List<Bitmap> srcBmps = new ArrayList();
    private float scaleVal = 1.0f;
    private Matrix matrix = new Matrix();
    public List<Bitmap> targetBmps = new ArrayList();
    private boolean bRunning = false;
    private Thread mThread = null;
    private boolean is_portrait = false;

    public void setIs_portrait(boolean z) {
        this.is_portrait = z;
    }

    public BmpUtils(Context context, ITargetBitmapFixListener iTargetBitmapFixListener) {
        this.mContext = context;
        this.onBitmapFixListener = iTargetBitmapFixListener;
    }

    public void setScale(int i) {
        float f = (i * 0.1f) + 1.0f;
        this.scaleVal = f;
        this.matrix.setScale(f, f);
    }

    public void setImageParam(int i, int i2) {
        if (this.imgWidth == i && this.imgHeight == i2) {
            return;
        }
        this.imgHeight = i2;
        this.imgWidth = i;
        synchronized (this.targetBmps) {
            this.targetBmps.clear();
        }
    }

    public void start() {
        this.bRunning = true;
        Thread thread = this.mThread;
        if (thread == null || !thread.isAlive()) {
            Thread thread2 = new Thread(this);
            this.mThread = thread2;
            thread2.start();
        }
    }

    public void push(Bitmap bitmap) {
        synchronized (this.srcBmps) {
            if (this.srcBmps.size() < 8) {
                this.srcBmps.add(bitmap);
            } else {
                this.srcBmps.clear();
                this.srcBmps.add(bitmap);
            }
        }
    }

    @Override // java.lang.Runnable
    public void run() {
        float f;
        this.bRunning = true;
        while (this.bRunning) {
            try {
                if (this.srcBmps.size() <= 0) {
                    Thread.sleep(2L);
                } else {
                    Bitmap bitmap = this.srcBmps.get(0);
                    if (bitmap == null) {
                        Thread.sleep(2L);
                    } else {
                        float f2 = this.imgWidth / this.imgHeight;
                        float width = bitmap.getWidth() / bitmap.getHeight();
                        if (f2 != width) {
                            float f3 = 0.0f;
                            if (f2 > width) {
                                f = (bitmap.getHeight() - ((bitmap.getWidth() * this.imgHeight) / this.imgWidth)) / 2;
                            } else {
                                f3 = (bitmap.getWidth() - ((bitmap.getHeight() * this.imgWidth) / this.imgHeight)) / 2;
                                f = 0.0f;
                            }
                            this.matrix.setScale(1.0f, 1.0f);
                            bitmap = Bitmap.createBitmap(bitmap, (int) f3, (int) f, (int) (bitmap.getWidth() - (f3 * 2.0f)), (int) (bitmap.getHeight() - (f * 2.0f)), this.matrix, true);
                        }
                        Matrix matrix = this.matrix;
                        float f4 = this.scaleVal;
                        matrix.setScale(f4, f4);
                        Bitmap createBitmap = Bitmap.createBitmap(bitmap, (int) ((bitmap.getWidth() * (1.0f - (1.0f / this.scaleVal))) / 2.0f), (int) ((bitmap.getHeight() * (1.0f - (1.0f / this.scaleVal))) / 2.0f), (int) (bitmap.getWidth() / this.scaleVal), (int) (bitmap.getHeight() / this.scaleVal), this.matrix, true);
                        this.srcBmps.remove(0);
                        ITargetBitmapFixListener iTargetBitmapFixListener = this.onBitmapFixListener;
                        if (iTargetBitmapFixListener != null) {
                            iTargetBitmapFixListener.OnBitmapFixed(createBitmap);
                        }
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.srcBmps.clear();
        this.targetBmps.clear();
    }

    public void zoomAdd() {
        float f = this.scaleVal;
        if (f < 50.0f) {
            this.scaleVal = f + 1.0f;
            Toast.makeText(this.mContext, "缩放比例为:" + this.scaleVal, 500).show();
        }
    }

    public void zoomSub() {
        float f = this.scaleVal;
        if (f > 1.0f) {
            this.scaleVal = f - 1.0f;
            Toast.makeText(this.mContext, "缩放比例为:" + this.scaleVal, 500).show();
        }
    }

    public void stop() {
        this.bRunning = false;
    }
}
