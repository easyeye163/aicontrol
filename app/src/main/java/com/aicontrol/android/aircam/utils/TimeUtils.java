package com.aicontrol.android.aircam.utils;

/* loaded from: classes5.dex */
public class TimeUtils {
    private int hour;
    private int minute;
    private int second;

    public TimeUtils(long j) {
        this.second = 0;
        this.minute = 0;
        this.hour = 0;
        int i = (int) j;
        this.second = i % 60;
        this.minute = (i / 60) % 60;
        this.hour = i / 3600;
    }

    public String getTime() {
        return String.format("%02d:%02d:%02d\n", Integer.valueOf(this.hour), Integer.valueOf(this.minute), Integer.valueOf(this.second));
    }
}
