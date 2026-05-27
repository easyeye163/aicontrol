package com.aicontrol.android.aircam.utils;

import android.os.Handler;
import java.util.Timer;
import java.util.TimerTask;

/* loaded from: classes5.dex */
public class TimerUtils {
    private int action;
    private long duration;
    private Handler mHandler;
    private Timer mTimer = null;
    private Task mTask = null;
    private boolean isTimerStart = false;
    LogUtils logEx = LogUtils.setLogger(TimerUtils.class);

    public TimerUtils(Handler handler, long j, int i) {
        this.mHandler = handler;
        this.action = i;
        this.duration = j;
    }

    private void initTimer() {
        if (this.mTimer == null) {
            this.mTimer = new Timer();
            this.mTask = new Task();
        }
    }

    private class Task extends TimerTask {
        private Task() {
        }

        @Override // java.util.TimerTask, java.lang.Runnable
        public void run() {
            if (TimerUtils.this.mHandler != null) {
                TimerUtils.this.mHandler.sendEmptyMessage(TimerUtils.this.action);
                TimerUtils.this.isTimerStart = false;
            }
        }
    }

    public void start() {
        if (this.mTimer == null) {
            initTimer();
            this.logEx.e("TimerUtils start");
            this.mTimer.schedule(this.mTask, 50L, this.duration);
        }
    }

    public void cancel() {
        if (this.mTimer != null) {
            this.logEx.e("TimerUtils cancel");
            this.mTimer.cancel();
            this.mTimer = null;
        }
    }
}
