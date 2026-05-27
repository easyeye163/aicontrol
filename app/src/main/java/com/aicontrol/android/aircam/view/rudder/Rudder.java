package com.aicontrol.android.aircam.view.rudder;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.aicontrol.android.aircam.utils.Constants;
import com.aicontrol.android.aircam.utils.LogUtils;
import com.aicontrol.android.aircam.utils.Util;
import com.aicontrol.android.aircam.view.listener.IRudderListener;
import com.aicontrol.android.aircam.view.pathdraw.AnimationPath;
import com.aicontrol.android.aircam.view.pathdraw.PathPoint;
import java.util.Collection;
import java.util.List;

/* loaded from: classes5.dex */
public class Rudder extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    public static final int AUTOPATH = 1;
    public static final int AUTOPATHPLAY = 2;
    public static final int NORMAL = 0;
    private static final String TAG = "Rudder";
    private AnimationPath autoPath;
    private boolean bRotate;
    private boolean bSensor;
    private boolean bStayHigh;
    private Bitmap bgBitmap;
    private int bmpBgWidth;
    private int bmpWidth;
    private RudderConfig config;
    private int ctrlMode;
    private int followPointerId;
    private volatile boolean isRunning;
    private Joystick leftJoystick;
    private final Object lock;
    private final LogUtils logUtils;
    private Paint mLeftPaint;
    private Paint mRightPaint;
    private Thread renderThread;
    private Joystick rightJoystick;
    private int rotateAction;
    private int rudderLen;
    private IRudderListener rudderListener;
    private int screenHeight;
    private int screenWidth;
    private SurfaceHolder surfaceHolder;
    private Bitmap thumbBitmap;
    private int vOffset;

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
    }

    public Rudder(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        Log.e(TAG, "=== Rudder constructor START ===");
        this.logUtils = LogUtils.setLogger(Rudder.class);
        this.renderThread = null;
        this.isRunning = false;
        this.lock = new Object();
        this.ctrlMode = 0;
        this.followPointerId = -1;
        this.bSensor = false;
        this.bRotate = false;
        this.rotateAction = 0;
        this.bStayHigh = false;
        this.config = RudderConfig.getDefault();
        init(context);
    }

    public void setConfig(RudderConfig rudderConfig) {
        this.config = rudderConfig;
        init(getContext());
        postInvalidate();
    }

    private void init(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        this.screenWidth = displayMetrics.widthPixels;
        this.screenHeight = displayMetrics.heightPixels;
        SurfaceHolder holder = getHolder();
        this.surfaceHolder = holder;
        holder.addCallback(this);
        this.surfaceHolder.setFormat(-2);
        setZOrderOnTop(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        Paint paint = new Paint();
        this.mLeftPaint = paint;
        paint.setColor(this.config.leftPaintColor);
        this.mLeftPaint.setAntiAlias(true);
        this.mLeftPaint.setAlpha(255);
        Paint paint2 = new Paint();
        this.mRightPaint = paint2;
        paint2.setColor(this.config.rightPaintColor);
        this.mRightPaint.setAntiAlias(true);
        this.mRightPaint.setStrokeCap(Paint.Cap.ROUND);
        this.mRightPaint.setAlpha(255);
        this.mRightPaint.setStrokeWidth(getResources().getDimension(this.config.strokeWidthRes));
        this.autoPath = new AnimationPath();
        this.leftJoystick = new Joystick(0);
        this.rightJoystick = new Joystick(1);
        initResourcesAndLayout();
    }

    private void initResourcesAndLayout() {
        boolean isLandscape = isLandscape();
        Bitmap decodeResource = BitmapFactory.decodeResource(getResources(), this.config.thumbResId);
        if (decodeResource == null) {
            this.logUtils.e("### thumbResId bitmap null, creating fallback");
            decodeResource = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        }
        Resources resources = getResources();
        RudderConfig rudderConfig = this.config;
        float dimension = resources.getDimension(isLandscape ? rudderConfig.thumbSizeLandRes : rudderConfig.thumbSizePortRes) / decodeResource.getWidth();
        Matrix matrix = new Matrix();
        matrix.setScale(dimension, dimension);
        Bitmap createBitmap = Bitmap.createBitmap(decodeResource, 0, 0, decodeResource.getWidth(), decodeResource.getHeight(), matrix, true);
        this.thumbBitmap = createBitmap;
        this.bmpWidth = createBitmap.getWidth() / 2;
        Bitmap decodeResource2 = BitmapFactory.decodeResource(getResources(), this.config.bgResId);
        if (decodeResource2 == null) {
            this.logUtils.e("### bgResId bitmap null, creating fallback");
            decodeResource2 = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        }
        Resources resources2 = getResources();
        RudderConfig rudderConfig2 = this.config;
        float dimension2 = resources2.getDimension(isLandscape ? rudderConfig2.bgSizeLandRes : rudderConfig2.bgSizePortRes);
        Resources resources3 = getResources();
        RudderConfig rudderConfig3 = this.config;
        this.vOffset = (int) resources3.getDimension(isLandscape ? rudderConfig3.vOffsetLandRes : rudderConfig3.vOffsetPortRes);
        float width = dimension2 / decodeResource2.getWidth();
        matrix.setScale(width, width);
        Bitmap createBitmap2 = Bitmap.createBitmap(decodeResource2, 0, 0, decodeResource2.getWidth(), decodeResource2.getHeight(), matrix, true);
        this.bgBitmap = createBitmap2;
        this.bmpBgWidth = createBitmap2.getWidth() / 2;
        int i = (int) ((dimension2 / 2.0f) - this.bmpWidth);
        this.rudderLen = i;
        int i2 = this.screenWidth / 4;
        int i3 = this.screenHeight;
        int i4 = isLandscape ? (i3 / 2) + this.vOffset : i3 - this.vOffset;
        this.leftJoystick.init(i2, i4, i2, i + i4);
        int i5 = (this.screenWidth * 3) / 4;
        int i6 = isLandscape ? (this.screenHeight / 2) + this.vOffset : this.screenHeight - this.vOffset;
        this.rightJoystick.init(i5, i6, i5, i6);
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == 2;
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        startRendering();
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        stopRendering();
    }

    private void startRendering() {
        Thread thread = this.renderThread;
        if (thread == null || !thread.isAlive()) {
            this.isRunning = true;
            Thread thread2 = new Thread(this);
            this.renderThread = thread2;
            thread2.start();
        }
    }

    private void stopRendering() {
        this.isRunning = false;
        try {
            Thread thread = this.renderThread;
            if (thread != null) {
                thread.join();
                this.renderThread = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void onDestroy() {
        stopRendering();
        SurfaceHolder surfaceHolder = this.surfaceHolder;
        if (surfaceHolder != null) {
            surfaceHolder.removeCallback(this);
        }
    }

    @Override // java.lang.Runnable
    public void run() {
        while (this.isRunning) {
            long currentTimeMillis = System.currentTimeMillis();
            updateJoysticks();
            Canvas canvas = null;
            try {
                try {
                    canvas = this.surfaceHolder.lockCanvas();
                    if (canvas != null) {
                        synchronized (this.lock) {
                            drawRudder(canvas);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (canvas != null) {
                        this.surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
                long currentTimeMillis2 = 16 - (System.currentTimeMillis() - currentTimeMillis);
                if (currentTimeMillis2 > 0) {
                    try {
                        Thread.sleep(currentTimeMillis2);
                    } catch (InterruptedException e2) {
                        e2.printStackTrace();
                    }
                }
            } finally {
                if (canvas != null) {
                    try {
                        this.surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (Exception e3) {
                        e3.printStackTrace();
                    }
                }
            }
        }
    }

    private void updateJoysticks() {
        this.leftJoystick.updateAnimation();
        this.rightJoystick.updateAnimation();
    }

    private void drawRudder(Canvas canvas) {
        int i = 0;
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        drawJoystick(canvas, this.leftJoystick);
        int i2 = this.ctrlMode;
        if (i2 == 0) {
            drawJoystick(canvas, this.rightJoystick);
            return;
        }
        if (i2 == 1 || i2 == 2) {
            canvas.drawBitmap(this.bgBitmap, this.rightJoystick.center.x - this.bmpBgWidth, this.rightJoystick.center.y - this.bmpBgWidth, this.mLeftPaint);
            List<PathPoint> lPoints = this.autoPath.getLPoints();
            if (lPoints.size() > 1) {
                while (i < lPoints.size() - 1) {
                    float f = lPoints.get(i).pointX;
                    float f2 = lPoints.get(i).pointY;
                    i++;
                    canvas.drawLine(f, f2, lPoints.get(i).pointX, lPoints.get(i).pointY, this.mRightPaint);
                }
            }
        }
    }

    private void drawJoystick(Canvas canvas, Joystick joystick) {
        int cx = joystick.center.x;
        int cy = joystick.center.y;
        int bgR = this.bmpBgWidth;
        int thR = this.bmpWidth;

        // Draw background circle programmatically
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(Math.max(3, bgR / 25));
        bgPaint.setColor(0x40FFFFFF);
        canvas.drawCircle(cx, cy, bgR, bgPaint);

        // Inner crosshair lines
        Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crossPaint.setStrokeWidth(1);
        crossPaint.setColor(0x25FFFFFF);
        canvas.drawLine(cx, cy - bgR + 10, cx, cy + bgR - 10, crossPaint);
        canvas.drawLine(cx - bgR + 10, cy, cx + bgR - 10, cy, crossPaint);

        // Inner reference circle
        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setStyle(Paint.Style.FILL);
        innerPaint.setColor(0x15FFFFFF);
        canvas.drawCircle(cx, cy, (int)(bgR * 0.35f), innerPaint);

        // Draw background bitmap on top (if valid)
        canvas.drawBitmap(this.bgBitmap, cx - this.bmpBgWidth, cy - this.bmpBgWidth, this.mLeftPaint);

        // Draw thumb
        canvas.save();
        float f = joystick.isPressed ? this.config.pressScale : 1.0f;
        float tx = joystick.current.x;
        float ty = joystick.current.y;
        canvas.scale(f, f, tx, ty);

        // Draw thumb glow
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(0x30FFFFFF);
        canvas.drawCircle(tx, ty, thR + 8, glowPaint);

        // Draw thumb circle
        Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(0xC8D0E0FF);
        canvas.drawCircle(tx, ty, thR, thumbPaint);

        // Thumb outline
        Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(Math.max(2, thR / 12));
        outlinePaint.setColor(0xE0FFFFFF);
        canvas.drawCircle(tx, ty, thR, outlinePaint);

        // Thumb highlight
        Paint hlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hlPaint.setStyle(Paint.Style.FILL);
        hlPaint.setColor(0x40FFFFFF);
        canvas.drawCircle(tx - thR * 0.2f, ty - thR * 0.2f, thR * 0.45f, hlPaint);

        canvas.restore();
    }

    @Override // android.view.View
    public boolean onTouchEvent(android.view.MotionEvent event) {
        int action = event.getActionMasked();
        if (action == android.view.MotionEvent.ACTION_DOWN) {
            int pid = event.getPointerId(0);
            float x = event.getX();
            float y = event.getY();
            if (this.ctrlMode == 0) {
                if (x < this.screenWidth / 2) {
                    this.leftJoystick.pointerId = pid;
                    this.leftJoystick.setPressed(true);
                    this.leftJoystick.update((int) x, (int) y);
                } else {
                    this.rightJoystick.pointerId = pid;
                    this.rightJoystick.setPressed(true);
                    this.rightJoystick.update((int) x, (int) y);
                }
            } else if (this.ctrlMode == 1 || this.ctrlMode == 2) {
                pathFollowInit((int) x, (int) y, pid);
            }
        } else if (action == android.view.MotionEvent.ACTION_POINTER_DOWN) {
            int idx = event.getActionIndex();
            float x2 = event.getX(idx);
            float y2 = event.getY(idx);
            int pid2 = event.getPointerId(idx);
            if (this.ctrlMode == 0) {
                if (x2 < this.screenWidth / 2 && !this.leftJoystick.isPressed) {
                    this.leftJoystick.pointerId = pid2;
                    this.leftJoystick.setPressed(true);
                    this.leftJoystick.update((int) x2, (int) y2);
                } else if (x2 >= this.screenWidth / 2 && !this.rightJoystick.isPressed) {
                    this.rightJoystick.pointerId = pid2;
                    this.rightJoystick.setPressed(true);
                    this.rightJoystick.update((int) x2, (int) y2);
                }
            }
        } else if (action == android.view.MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int pid3 = event.getPointerId(i);
                float mx = event.getX(i);
                float my = event.getY(i);
                if (this.ctrlMode == 1 || this.ctrlMode == 2) {
                    if (pid3 == this.followPointerId) {
                        this.autoPath.lineTo((int) mx, (int) my);
                    }
                } else {
                    if (pid3 == this.leftJoystick.pointerId && this.leftJoystick.isPressed) {
                        this.leftJoystick.update((int) mx, (int) my);
                    }
                    if (pid3 == this.rightJoystick.pointerId && this.rightJoystick.isPressed) {
                        this.rightJoystick.update((int) mx, (int) my);
                    }
                }
            }
        } else if (action == android.view.MotionEvent.ACTION_UP) {
            this.leftJoystick.setPressed(false);
            this.leftJoystick.reset();
            this.rightJoystick.setPressed(false);
            this.rightJoystick.reset();
            if (this.ctrlMode == 1) {
                pathFollowUp();
            }
        } else if (action == android.view.MotionEvent.ACTION_POINTER_UP) {
            int idx2 = event.getActionIndex();
            int releasedPid = event.getPointerId(idx2);
            if (releasedPid == this.leftJoystick.pointerId) {
                this.leftJoystick.setPressed(false);
                this.leftJoystick.reset();
                this.leftJoystick.pointerId = -1;
            }
            if (releasedPid == this.rightJoystick.pointerId) {
                this.rightJoystick.setPressed(false);
                this.rightJoystick.reset();
                this.rightJoystick.pointerId = -1;
            }
            if (releasedPid == this.followPointerId) {
                pathFollowUp();
                this.followPointerId = -1;
            }
        }
        return true;
    }

    public void onPathFollowRegister() {
        this.autoPath.clear();
        this.ctrlMode = 1;
    }

    public void onPathFollowUnregister() {
        this.autoPath.clear();
        this.ctrlMode = 0;
        this.rightJoystick.resetToCenter();
    }

    public void pathFollowInit(int i, int i2, int i3) {
        if (i > this.screenWidth / 2) {
            this.autoPath.clear();
            this.followPointerId = i3;
            this.autoPath.lineTo(i, i2);
            IRudderListener iRudderListener = this.rudderListener;
            if (iRudderListener != null) {
                iRudderListener.onPathFollowNotify(true, null);
            }
        }
    }

    public void pathFollowUp() {
        IRudderListener iRudderListener = this.rudderListener;
        if (iRudderListener != null) {
            iRudderListener.onPathFollowNotify(false, this.autoPath.getPoints());
        }
    }

    public Collection<PathPoint> getPathPoint() {
        return this.autoPath.getPoints();
    }

    public PathPoint getEndPathPoint() {
        return this.autoPath.getEndPoint();
    }

    public float getPathLen() {
        return this.autoPath.getPathLen();
    }

    public void dealWithAccRudder(int i, int i2) {
        this.leftJoystick.processUpdate(i, i2);
    }

    public void dealWidhDirRudder(int i, int i2, boolean z) {
        this.rightJoystick.processUpdate(i, i2, z);
    }

    public Point getAccDefaultPosition() {
        return this.leftJoystick.defaultPos;
    }

    public int getAccCenterY() {
        return this.leftJoystick.center.y;
    }

    public Point getDirDefaultPosition() {
        return this.rightJoystick.defaultPos;
    }

    public int getRudderLen() {
        return this.rudderLen;
    }

    public void registerListener(IRudderListener iRudderListener) {
        this.rudderListener = iRudderListener;
    }

    public void sensorRegister() {
        this.bSensor = true;
        this.rightJoystick.reset();
    }

    public void sensorUnregister() {
        this.bSensor = false;
        this.rightJoystick.reset();
    }

    public void onSensorNotify(int i, int i2) {
        this.rightJoystick.processUpdate(((i * this.rudderLen) + (Constants.MAX_DIR_H_VALUE * this.rightJoystick.defaultPos.x)) / Constants.MAX_DIR_H_VALUE, ((i2 * this.rudderLen) + (Constants.MAX_DIR_O_VALUE * this.rightJoystick.defaultPos.y)) / Constants.MAX_DIR_O_VALUE, false);
    }

    public void onNotifySpeed() {
        Joystick joystick = this.leftJoystick;
        joystick.processUpdate(joystick.current.x, this.leftJoystick.current.y);
        Joystick joystick2 = this.rightJoystick;
        joystick2.processUpdate(joystick2.current.x, this.rightJoystick.current.y);
    }

    public void onRegisterRotate() {
        this.bRotate = true;
        this.rotateAction = 0;
    }

    public void onUnregisterRotate() {
        synchronized (this.lock) {
            this.bRotate = false;
            this.rotateAction = 0;
        }
        Joystick joystick = this.rightJoystick;
        joystick.processUpdate(joystick.current.x, this.rightJoystick.current.y);
        this.logUtils.e("###onUnregisterRotate ");
    }

    public void onRegisterStayHighMode() {
        this.bStayHigh = true;
        this.leftJoystick.reset();
    }

    public void onUnregisterStayHighMode() {
        this.bStayHigh = false;
        this.leftJoystick.reset();
    }

    public void invalidateValue() {
        Joystick joystick = this.leftJoystick;
        joystick.processUpdate(joystick.current.x, this.leftJoystick.current.y);
        Joystick joystick2 = this.rightJoystick;
        joystick2.processUpdate(joystick2.current.x, this.rightJoystick.current.y, this.bSensor);
    }

    private class Joystick {
        static final int TYPE_DIRECTION = 1;
        static final int TYPE_THROTTLE = 0;
        int type;
        Point center = new Point();
        Point defaultPos = new Point();
        Point current = new Point();
        Point targetPos = new Point();
        boolean isDragging = false;
        boolean isPressed = false;
        int pointerId = -1;

        Joystick(int i) {
            this.type = i;
        }

        void init(int i, int i2, int i3, int i4) {
            this.center.set(i, i2);
            this.defaultPos.set(i3, i4);
            if (this.type == 0) {
                Point point = this.current;
                if (!Rudder.this.bStayHigh) {
                    i2 = i4;
                }
                point.set(i3, i2);
            } else {
                this.current.set(i3, i4);
            }
            this.targetPos.set(this.current.x, this.current.y);
        }

        void setPressed(boolean z) {
            this.isPressed = z;
        }

        boolean isTouchInside(int i, int i2) {
            int i3 = Rudder.this.bmpWidth * 2;
            return Math.abs(i - this.current.x) <= i3 && Math.abs(i2 - this.current.y) <= i3;
        }

        void update(int i, int i2) {
            this.isDragging = true;
            processUpdate(i, i2);
        }

        void updateAnimation() {
            if (this.isDragging) {
                return;
            }
            float f = Rudder.this.config.smoothing;
            int i = this.targetPos.x - this.current.x;
            int i2 = this.targetPos.y - this.current.y;
            if (Math.abs(i) > 1 || Math.abs(i2) > 1) {
                this.current.x += (int) (i * f);
                this.current.y += (int) (i2 * f);
                processUpdate(this.current.x, this.current.y, false);
                return;
            }
            this.current.set(this.targetPos.x, this.targetPos.y);
        }

        void processUpdate(int i, int i2) {
            processUpdate(i, i2, false);
        }

        void processUpdate(int i, int i2, boolean z) {
            if (this.type == 1 && z) {
                return;
            }
            if (Util.Length(i, i2, this.center.x, this.center.y) >= Rudder.this.rudderLen) {
                this.current = Util.UpdatePoint(i, i2, this.center.x, this.center.y, Rudder.this.rudderLen);
            } else {
                this.current.set(i, i2);
            }
            int i3 = this.current.x >= this.defaultPos.x ? 1 : -1;
            int i4 = this.current.y <= this.defaultPos.y ? 1 : -1;
            int i5 = -128;
            int i6 = -93;
            if (this.type == 0) {
                int GetLR = Util.GetLR(this.current.x, Rudder.this.rudderLen, this.defaultPos.x, Constants.MAX_ACC_H_VALUE);
                int GetUpDown = Util.GetUpDown(this.current.y, Rudder.this.rudderLen * 2, this.defaultPos.y, Constants.MAX_POW_VALUE);
                byte b = (byte) ((i3 * GetLR) + 128);
                if (b == -95) {
                    i5 = -93;
                } else if (Math.abs(b - 128) > Constants.CUR_OFFSET_VALUE) {
                    i5 = b;
                }
                if (GetUpDown != -95) {
                    if (GetUpDown < 4) {
                        i6 = 0;
                    } else {
                        i6 = Math.abs(GetUpDown + (-128)) <= Constants.CUR_OFFSET_VALUE ? 128 : GetUpDown;
                    }
                }
                if (Rudder.this.rudderListener != null) {
                    Rudder.this.rudderListener.onAccNotify(i5, i6);
                    return;
                }
                return;
            }
            int GetLR2 = Util.GetLR(this.current.x, Rudder.this.rudderLen, this.defaultPos.x, Constants.MAX_DIR_H_VALUE);
            int GetUpDown2 = Util.GetUpDown(this.current.y, Rudder.this.rudderLen, this.defaultPos.y, Constants.MAX_DIR_O_VALUE);
            byte b2 = (byte) ((i3 * GetLR2) + 128);
            int i7 = (i4 * GetUpDown2) + 128;
            if (i7 >= 255) {
                i7 = 255;
            }
            int i8 = (i4 == 1 && i7 == 0) ? 255 : i7;
            if (b2 == -95) {
                b2 = -93;
            } else if (Math.abs(b2 - 128) <= Constants.CUR_OFFSET_VALUE) {
                b2 = Byte.MIN_VALUE;
            }
            if (i8 == -95) {
                i5 = -93;
            } else if (Math.abs(i8 - 128) > Constants.CUR_OFFSET_VALUE) {
                i5 = i8;
            }
            if (Rudder.this.bRotate) {
                handleRotation(GetLR2, GetUpDown2, i3, i4);
            }
            if (Rudder.this.rudderListener != null) {
                Rudder.this.rudderListener.onDirNotify(b2, i5, Rudder.this.rotateAction);
            }
        }

        private void handleRotation(int i, int i2, int i3, int i4) {
            if (i > Constants.MAX_DIR_H_VALUE / 2) {
                Rudder.this.rotateAction = i3 == 1 ? 1 : 2;
                Log.e(Rudder.TAG, "rotateAction = " + Rudder.this.rotateAction);
                return;
            }
            if (i2 > Constants.MAX_DIR_O_VALUE / 2) {
                Rudder.this.rotateAction = i4 == 1 ? 4 : 3;
                Log.e(Rudder.TAG, "rotateAction = " + Rudder.this.rotateAction);
            }
        }

        void reset() {
            this.isDragging = false;
            if (this.type == 0) {
                if (Rudder.this.bStayHigh) {
                    this.targetPos.set(this.defaultPos.x, this.center.y);
                    return;
                } else {
                    this.targetPos.set(this.defaultPos.x, this.current.y);
                    return;
                }
            }
            this.targetPos.set(this.defaultPos.x, this.defaultPos.y);
        }

        void resetToCenter() {
            this.isDragging = false;
            this.targetPos.set(this.defaultPos.x, this.defaultPos.y);
        }
    }
}
