package com.aicontrol.android.aircam.presenter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaScannerConnection;
import com.aicontrol.android.aircam.utils.LogUtils;
import com.aicontrol.android.aircam.utils.PathUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/* loaded from: classes5.dex */
public class Snap implements Runnable {
    private static int PHOTO_SNAP = 0;
    private static float SCALE_FACTOR = 1.0f;
    private static int VIDEO_SNAP = 1;
    private Context mContext;
    private PathUtils pathUtils;
    private File photoFile = null;
    private long startTime = 0;
    private boolean bTakePhoto = false;
    LogUtils logUtils = LogUtils.setLogger(Snap.class);
    private Thread mThread = null;
    private Bitmap photoBmp = null;
    private Matrix matrix = new Matrix();
    private float scaleX = 0.0f;
    private float scaleY = 0.0f;
    private boolean bBmpRotate = false;
    private String videoPhoto = null;
    private int snapMode = PHOTO_SNAP;
    private boolean bVideoSnap = false;

    public Snap(Context context) {
        this.mContext = null;
        this.pathUtils = null;
        this.mContext = context;
        this.pathUtils = new PathUtils(context);
    }

    public void prepareSnap(int i, int i2, boolean z) {
        try {
            this.photoFile = this.pathUtils.createPhotoFile();
            this.startTime = System.currentTimeMillis();
            if (i == 0) {
                SCALE_FACTOR = 2.0f;
            } else if (i == 1) {
                SCALE_FACTOR = 3.0f;
            } else if (i == 2) {
                SCALE_FACTOR = 6.0f;
            }
            this.bTakePhoto = true;
            this.bBmpRotate = z;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void videoPrepareSnap(int i, int i2, String str) {
        try {
            File file = new File(str);
            this.photoFile = file;
            if (file.exists()) {
                this.photoFile.createNewFile();
            }
            if (i == 0) {
                SCALE_FACTOR = 2.0f;
            } else if (i == 1) {
                SCALE_FACTOR = 3.0f;
            } else if (i == 2) {
                SCALE_FACTOR = 6.0f;
            }
            this.bVideoSnap = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void takePhoto(final Bitmap bitmap) {
        new Thread(new Runnable() { // from class: com.tzh.wifi.wificam.presenter.Snap.1
            @Override // java.lang.Runnable
            public void run() {
                Snap.this.scaleX = Snap.SCALE_FACTOR;
                Snap.this.scaleY = Snap.SCALE_FACTOR;
                Snap.this.matrix.setScale(Snap.this.scaleX, Snap.this.scaleY);
                if (Snap.this.bBmpRotate) {
                    Snap.this.matrix.postRotate(180.0f);
                }
                Bitmap bitmap2 = bitmap;
                Bitmap createBitmap = Bitmap.createBitmap(bitmap2, 0, 0, bitmap2.getWidth(), bitmap.getHeight(), Snap.this.matrix, true);
                if (createBitmap != null) {
                    try {
                        FileOutputStream fileOutputStream = new FileOutputStream(Snap.this.photoFile);
                        Snap.this.logUtils.e("width:" + createBitmap.getWidth() + " height:" + createBitmap.getHeight());
                        createBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
                        fileOutputStream.flush();
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void videoTakeSnap(Bitmap bitmap) {
        if (this.bVideoSnap) {
            this.bVideoSnap = false;
            takePhoto(bitmap);
        }
    }

    private void startSnap() {
        Thread thread = this.mThread;
        if (thread == null || !thread.isAlive()) {
            Thread thread2 = new Thread(this);
            this.mThread = thread2;
            thread2.start();
        }
    }

    public void takeSnap(Bitmap bitmap) {
        if (this.photoFile == null || !this.bTakePhoto) {
            return;
        }
        this.photoBmp = bitmap;
        startSnap();
    }

    private void saveSnapPhoto(File file, Bitmap bitmap) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            this.logUtils.e("width:" + bitmap.getWidth() + " height:" + bitmap.getHeight());
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaScannerConnection.scanFile(this.mContext, new String[]{file.toString()}, null, null);
    }

    @Override // java.lang.Runnable
    public void run() {
        if (this.photoBmp == null) {
            return;
        }
        float f = SCALE_FACTOR;
        this.scaleX = f;
        this.scaleY = f;
        this.matrix.setScale(f, f);
        if (this.bBmpRotate) {
            this.matrix.postRotate(180.0f);
        }
        Bitmap bitmap = this.photoBmp;
        Bitmap createBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), this.photoBmp.getHeight(), this.matrix, true);
        if (createBitmap != null) {
            saveSnapPhoto(this.photoFile, createBitmap);
        }
        this.bTakePhoto = false;
    }
}
