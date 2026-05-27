package com.aicontrol.android.aircam.utils;

import android.content.Context;
import android.content.SharedPreferences;

/* loaded from: classes5.dex */
public class ConfigUtils {
    public static void writeLan(Context context, int i) {
        SharedPreferences.Editor edit = context.getSharedPreferences("wifiZoneLanguage", 0).edit();
        edit.putInt("lanague", i);
        edit.commit();
    }

    public static int getLan(Context context) {
        return context.getSharedPreferences("wifiZoneLanguage", 0).getInt("lanague", 0);
    }

    public static void writeIsShow(Context context, int i) {
        SharedPreferences.Editor edit = context.getSharedPreferences("isShowDia", 0).edit();
        edit.putInt("isShow", i);
        edit.commit();
    }

    public static int getIsShow(Context context) {
        return context.getSharedPreferences("isShowDia", 0).getInt("isShow", 1);
    }

    public static void writeRotateCfg(Context context, int i) {
        SharedPreferences.Editor edit = context.getSharedPreferences("writePicShowCfg", 0).edit();
        edit.putInt("angle", i);
        edit.commit();
    }

    public static int getRotateCfg(Context context) {
        return context.getSharedPreferences("writePicShowCfg", 0).getInt("angle", 0);
    }

    public static void writeLeftTune(Context context, int i) {
        SharedPreferences.Editor edit = context.getSharedPreferences("writeLeftTune", 0).edit();
        edit.putInt("leftTune", i);
        edit.commit();
    }

    public static int getLeftTune(Context context) {
        return context.getSharedPreferences("writeLeftTune", 0).getInt("leftTune", 16);
    }

    public static void writeRightTune(Context context, int i) {
        SharedPreferences.Editor edit = context.getSharedPreferences("writeRightTune", 0).edit();
        edit.putInt("rightTune", i);
        edit.commit();
    }

    public static int getRightTune(Context context) {
        return context.getSharedPreferences("writeRightTune", 0).getInt("rightTune", 16);
    }

    public static void writeCenterTune(Context context, int i) {
        SharedPreferences.Editor edit = context.getSharedPreferences("writeCenterTune", 0).edit();
        edit.putInt("centerTune", i);
        edit.commit();
    }

    public static int getCenterTune(Context context) {
        return context.getSharedPreferences("writeCenterTune", 0).getInt("centerTune", 16);
    }

    public static void writePlayBackRotateCfg(Context context, int i) {
        SharedPreferences.Editor edit = context.getSharedPreferences("writePhotoPlayCfg", 0).edit();
        edit.putInt("angle", i);
        edit.commit();
    }

    public static int getPlayBackRotateCfg(Context context) {
        return context.getSharedPreferences("writePhotoPlayCfg", 0).getInt("angle", 0);
    }

    public static void writeVideoPlayRotateCfg(Context context, int i) {
        SharedPreferences.Editor edit = context.getSharedPreferences("writeVideoPlayRotateCfg", 0).edit();
        edit.putInt("angle", i);
        edit.commit();
    }

    public static int getVideoPlayRotateCfg(Context context) {
        return context.getSharedPreferences("writeVideoPlayRotateCfg", 0).getInt("angle", 0);
    }
}
