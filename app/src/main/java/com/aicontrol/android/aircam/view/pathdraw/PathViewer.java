package com.aicontrol.android.aircam.view.pathdraw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import androidx.core.view.MotionEventCompat;
import com.aicontrol.android.aircam.utils.LogUtils;
import java.util.Collection;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/* loaded from: classes5.dex */
public class PathViewer extends GLSurfaceView implements SurfaceHolder.Callback, Runnable {
    boolean isPowerChange;
    LogUtils logEx;
    private AnimationPath mAnPath;
    private SurfaceHolder mHolder;
    private Paint mLeftPaint;
    Path mPath;
    private MyRenderer mRenderer;
    private Paint mRightPaint;

    @Override // java.lang.Runnable
    public void run() {
    }

    @Override // android.opengl.GLSurfaceView, android.view.SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
    }

    @Override // android.opengl.GLSurfaceView, android.view.SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
    }

    @Override // android.opengl.GLSurfaceView, android.view.SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    }

    public PathViewer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mRenderer = null;
        this.mHolder = null;
        this.mLeftPaint = null;
        this.mRightPaint = null;
        this.logEx = LogUtils.setLogger(PathViewer.class);
        this.isPowerChange = false;
        this.mPath = null;
        this.mAnPath = null;
        SurfaceHolder holder = getHolder();
        this.mHolder = holder;
        holder.addCallback(this);
        Paint paint = new Paint();
        this.mLeftPaint = paint;
        paint.setColor(-65536);
        this.mLeftPaint.setAntiAlias(false);
        this.mLeftPaint.setStyle(Paint.Style.STROKE);
        this.mLeftPaint.setDither(true);
        this.mLeftPaint.setStrokeWidth(10.0f);
        this.mLeftPaint.setHinting(5);
        this.mLeftPaint.setStrokeCap(Paint.Cap.ROUND);
        Paint paint2 = new Paint();
        this.mRightPaint = paint2;
        paint2.setColor(-3355444);
        this.mRightPaint.setAntiAlias(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setZOrderOnTop(true);
        this.mHolder.setFormat(-2);
        this.mAnPath = new AnimationPath();
        MyRenderer myRenderer = new MyRenderer();
        this.mRenderer = myRenderer;
        setRenderer(myRenderer);
        this.mPath = new Path();
    }

    public Collection<PathPoint> getPoints() {
        return this.mAnPath.getPoints();
    }

    public PathPoint getEndPoint() {
        return this.mAnPath.getEndPoint();
    }

    public float getPathLen() {
        return this.mAnPath.getPathLen();
    }

    @Override // android.view.SurfaceView, android.view.View
    public void draw(Canvas canvas) {
        super.draw(canvas);
        int i = 0;
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        List<PathPoint> lPoints = this.mAnPath.getLPoints();
        while (i < lPoints.size() - 1) {
            float f = lPoints.get(i).pointX;
            float f2 = lPoints.get(i).pointY;
            i++;
            canvas.drawLine(f, f2, lPoints.get(i).pointX, lPoints.get(i).pointY, this.mLeftPaint);
        }
    }

    @Override // android.view.View
    public boolean onTouchEvent(MotionEvent motionEvent) {
        motionEvent.getPointerCount();
        int action = (motionEvent.getAction() & MotionEventCompat.ACTION_POINTER_INDEX_MASK) >>> 8;
        int action2 = motionEvent.getAction();
        if (action2 == 0) {
            this.mAnPath.clear();
            invalidate();
            this.mAnPath.lineTo((int) motionEvent.getX(action), (int) motionEvent.getY(action));
            return true;
        }
        if (action2 != 2) {
            return true;
        }
        this.mAnPath.lineTo((int) motionEvent.getX(action), (int) motionEvent.getY(action));
        invalidate();
        return true;
    }

    public class MyRenderer implements GLSurfaceView.Renderer {
        @Override // android.opengl.GLSurfaceView.Renderer
        public void onDrawFrame(GL10 gl10) {
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onSurfaceCreated(GL10 gl10, EGLConfig eGLConfig) {
        }

        public MyRenderer() {
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onSurfaceChanged(GL10 gl10, int i, int i2) {
            gl10.glViewport(0, 0, i, i2);
        }
    }
}
