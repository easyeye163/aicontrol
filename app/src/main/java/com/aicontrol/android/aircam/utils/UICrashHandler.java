package com.aicontrol.android.aircam.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.widget.Toast;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/* loaded from: classes5.dex */
public class UICrashHandler implements Thread.UncaughtExceptionHandler {
    private static UICrashHandler INSTANCE = new UICrashHandler();
    public static final int LogFileLimit = 10;
    private Context mContext;
    private Thread.UncaughtExceptionHandler mDefaultHandler;
    private Map<String, String> infos = new HashMap();
    LogUtils logEx = LogUtils.setLogger(UICrashHandler.class);
    Comparator<File> newfileFinder = new Comparator<File>() { // from class: com.tzh.wifi.wificam.utils.UICrashHandler.1
        @Override // java.util.Comparator
        public int compare(File file, File file2) {
            if (file.lastModified() > file2.lastModified()) {
                return 1;
            }
            return file.lastModified() < file2.lastModified() ? -1 : 0;
        }
    };

    private UICrashHandler() {
    }

    public static UICrashHandler getInstance() {
        return INSTANCE;
    }

    public void init(Context context) {
        this.mContext = context;
        this.mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override // java.lang.Thread.UncaughtExceptionHandler
    public void uncaughtException(Thread thread, Throwable th) {
        Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
        if (!handleException(th) && (uncaughtExceptionHandler = this.mDefaultHandler) != null) {
            uncaughtExceptionHandler.uncaughtException(thread, th);
            return;
        }
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e) {
            this.logEx.e("error : " + e);
        }
        Process.killProcess(Process.myPid());
        System.exit(1);
    }

    public void collectDeviceInfo(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 1);
            if (packageInfo != null) {
                String str = packageInfo.versionName == null ? "null" : packageInfo.versionName;
                String str2 = packageInfo.versionCode + "";
                this.infos.put("versionName", str);
                this.infos.put("versionCode", str2);
            }
        } catch (PackageManager.NameNotFoundException e) {
            this.logEx.e("an error occured when collect package info  " + e);
        }
        for (Field field : Build.class.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                this.infos.put(field.getName(), field.get(null).toString());
            } catch (Exception e2) {
                this.logEx.e("an error occured when collect crash info  " + e2);
            }
        }
    }

    private int saveCrashInfo2File(Throwable th) {
        StringBuffer stringBuffer = new StringBuffer();
        for (Map.Entry<String, String> entry : this.infos.entrySet()) {
            stringBuffer.append(entry.getKey() + " = " + entry.getValue() + "\n");
        }
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        th.printStackTrace(printWriter);
        for (Throwable cause = th.getCause(); cause != null; cause = cause.getCause()) {
            cause.printStackTrace(printWriter);
        }
        printWriter.close();
        stringBuffer.append(stringWriter.toString());
        this.logEx.e("result:  " + stringBuffer.toString());
        return 1;
    }

    /* JADX WARN: Type inference failed for: r0v0, types: [com.tzh.wifi.wificam.utils.UICrashHandler$2] */
    private boolean handleException(Throwable th) {
        if (th == null) {
            return false;
        }
        new Thread() { // from class: com.tzh.wifi.wificam.utils.UICrashHandler.2
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                Looper.prepare();
                Toast.makeText(UICrashHandler.this.mContext, "App error!", 1).show();
                Looper.loop();
            }
        }.start();
        collectDeviceInfo(this.mContext);
        saveCrashInfo2File(th);
        return true;
    }
}
