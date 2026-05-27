package com.aicontrol.android.aircam.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.aicontrol.android.R;

/* loaded from: classes5.dex */
public class SurfaceViews extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "surfaceview";
    private Bitmap mBitmap;
    private Canvas m_canvas;
    private SurfaceHolder m_holder;
    private Paint m_paint;
    private Matrix matrix;
    private Rect rect;
    private int roateAngle;

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    }

    public SurfaceViews(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.m_holder = null;
        this.m_canvas = null;
        this.m_paint = null;
        this.rect = null;
        this.matrix = null;
        this.roateAngle = 0;
        this.mBitmap = null;
        SurfaceHolder holder = getHolder();
        this.m_holder = holder;
        holder.addCallback(this);
        Paint paint = new Paint();
        this.m_paint = paint;
        paint.setColor(-16776961);
        this.m_paint.setAntiAlias(true);
        this.matrix = new Matrix();
        this.m_holder.setFormat(-2);
        this.rect = new Rect(0, 0, getWidth(), getHeight());
        Log.e(TAG, "width " + getWidth() + " height " + getHeight());
        this.mBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.play_bg_icon);
        if (this.mBitmap == null) {
            this.mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        }
    }

    public void setRotateAngle(int i) {
        this.roateAngle = i;
    }

    public void SetBitmap(Bitmap bitmap) {
        Canvas lockCanvas = this.m_holder.lockCanvas(this.rect);
        this.m_canvas = lockCanvas;
        if (lockCanvas == null) {
            try {
                Thread.sleep(5L);
                return;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
        lockCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        this.m_canvas.clipRect(this.rect);
        this.m_canvas.drawBitmap(Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), this.matrix, true), (Rect) null, this.rect, this.m_paint);
        SurfaceHolder surfaceHolder = this.m_holder;
        if (surfaceHolder != null) {
            surfaceHolder.unlockCanvasAndPost(this.m_canvas);
        }
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
        SetBitmap(this.mBitmap);
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.e(TAG, "width " + getWidth() + " height " + getHeight());
        SetBitmap(this.mBitmap);
    }

    public void disconnect() {
        Bitmap decodeResource = BitmapFactory.decodeResource(getResources(), R.mipmap.play_bg_icon);
        if (decodeResource == null) {
            decodeResource = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        }
        this.mBitmap = decodeResource;
        SetBitmap(decodeResource);
    }
}
