package com.aicontrol.android.aircam.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.view.MotionEventCompat;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.utils.LogUtils;
import com.aicontrol.android.aircam.view.listener.IScaleListener;

/* loaded from: classes5.dex */
public class ZoomView extends View {
    private int height;
    private int lineHeight;
    private int lineWidth;
    LogUtils logUtils;
    private LayoutInflater mInflater;
    private Paint mPaint;
    private Path mPath;
    private Point mPoint;
    private Bitmap mSliderBar;
    private Bitmap mThumb;
    private int roundR;
    private IScaleListener scaleListener;
    private int touchID;
    private int width;

    public ZoomView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mPaint = new Paint();
        this.logUtils = LogUtils.setLogger(ZoomView.class);
        this.width = 0;
        this.height = 0;
        this.mInflater = null;
        this.touchID = 0;
        this.mSliderBar = null;
        this.mThumb = null;
        this.mPoint = new Point();
        this.mPath = new Path();
        this.lineWidth = 0;
        this.lineHeight = 0;
        this.roundR = 0;
        this.mSliderBar = BitmapFactory.decodeResource(getResources(), R.mipmap.play_slider_portrait_bar);
        if (this.mSliderBar == null) {
            this.mSliderBar = Bitmap.createBitmap(20, 300, Bitmap.Config.ARGB_8888);
        }
        this.mThumb = BitmapFactory.decodeResource(getResources(), R.mipmap.play_slider_portrait_thumb_icon);
        if (this.mThumb == null) {
            this.mThumb = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        }
        this.lineWidth = (int) getResources().getDimension(R.dimen.y15);
        this.lineHeight = (int) getResources().getDimension(R.dimen.y350);
        this.roundR = (int) getResources().getDimension(R.dimen.y21);
        this.logUtils.e("###zoomview slider:" + this.mSliderBar.getWidth() + "   " + this.mSliderBar.getHeight() + " " + getWidth() + "  " + getHeight());
    }

    public void setDelegate(IScaleListener iScaleListener) {
        this.scaleListener = iScaleListener;
    }

    @Override // android.view.View
    public boolean onTouchEvent(MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        if (action == 0) {
            dealWithTouchDown(motionEvent);
        } else if (action == 1) {
            dealWithTouchUp(motionEvent);
        } else if (action == 2) {
            dealWithTouchMove(motionEvent);
        }
        return true;
    }

    public void dealWithTouchDown(MotionEvent motionEvent) {
        this.touchID = (motionEvent.getAction() & MotionEventCompat.ACTION_POINTER_INDEX_MASK) >>> 8;
        int y = (int) motionEvent.getY();
        int height = getHeight();
        int i = this.roundR;
        if (y > height - (i / 2)) {
            this.mPoint.y = getHeight() - (this.roundR / 2);
        } else if (y <= i / 2) {
            this.mPoint.y = i / 2;
        } else {
            this.mPoint.y = y;
        }
        IScaleListener iScaleListener = this.scaleListener;
        if (iScaleListener != null) {
            iScaleListener.OnZoomStart();
        }
    }

    public void dealWithTouchUp(MotionEvent motionEvent) {
        this.touchID = -1;
        IScaleListener iScaleListener = this.scaleListener;
        if (iScaleListener != null) {
            iScaleListener.OnZoomEnd();
        }
    }

    public void dealWithTouchMove(MotionEvent motionEvent) {
        int pointerCount = motionEvent.getPointerCount();
        for (int i = 0; i < pointerCount; i++) {
            int y = (int) motionEvent.getY(i);
            if (i == this.touchID) {
                int height = getHeight();
                int i2 = this.roundR;
                if (y >= height - i2) {
                    this.mPoint.y = getHeight() - this.roundR;
                } else if (y <= i2) {
                    this.mPoint.y = i2;
                } else {
                    this.mPoint.y = y;
                }
                caculateZoomView();
                invalidate();
            }
        }
    }

    public void dealWithrest() {
        this.mPoint.y = 329;
    }

    public void caculateZoomView() {
        int abs = (Math.abs((this.mPoint.y - getHeight()) + this.roundR) * 49) / (getHeight() - (this.roundR * 2));
        IScaleListener iScaleListener = this.scaleListener;
        if (iScaleListener != null) {
            iScaleListener.OnZoomSet(abs);
        }
        this.logUtils.e("###percent:" + abs);
    }

    @Override // android.view.View
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        this.mPaint.setColor(getResources().getColor(R.color.lightgrey));
        canvas.drawRoundRect(new RectF((getWidth() - this.lineWidth) / 2, this.roundR, (getWidth() / 2) + (this.lineWidth / 2), getHeight() - this.roundR), 8.0f, 8.0f, this.mPaint);
        this.logUtils.e("###width:" + getWidth() + "  " + getHeight() + " " + this.mPoint.x + " " + this.mPoint.y);
        this.mPaint.setColor(getResources().getColor(R.color.white));
        this.mPaint.setAlpha(100);
        if (this.mPoint.y == 0) {
            this.mPoint.y = getHeight() - this.roundR;
        }
        canvas.drawCircle(getWidth() / 2, this.mPoint.y, this.roundR, this.mPaint);
        this.mPaint.setColor(getResources().getColor(R.color.colorSeekBar));
        canvas.drawCircle(getWidth() / 2, this.mPoint.y, this.lineWidth / 2, this.mPaint);
    }
}
