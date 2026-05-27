package com.aicontrol.android.aircam.utils;

import android.content.Context;
import com.aicontrol.android.R;

/* loaded from: classes5.dex */
public class StringUtils {
    private static StringUtils mInstance;
    private Context context;
    public String strNoSpace = "";
    public String strNoConnect = "";
    public String strNoSupport = "";
    public String strConnected = "";
    public String strDeleteTitle = "";
    public String strDeleteContent = "";
    public String strDeleteConfirm = "";
    public String strDeleteCancel = "";
    public String strRecording = "";
    public String strGsensorEnable = "";
    public String strVRState = "";
    public String strPullUp = "";
    public String strPullDown = "";
    public String strGsnsorNo = "";
    public String strLockDown = "";
    public String strRelFinger = "";
    public String strEncoding = "";
    public String strUploadTitle = "";
    public String strUploadContent = "";
    public String strSelect = "";
    public String strCloseMusic = "";
    public String strConfirm = "";
    public String strPopularMusic = "";
    public String strLocalMusic = "";
    public String strSnap = "";
    public String strRecordStart = "";
    public String strRecordEnd = "";
    public String strPermissionRequired = "";
    public String strPermissionGranted = "";
    public String strPermissionDenied = "";

    private StringUtils(Context context) {
        this.context = context;
    }

    public static StringUtils getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new StringUtils(context);
        }
        return mInstance;
    }

    public void changeLan(int i) {
        if (i == 0) {
            change2Cn();
            return;
        }
        if (i == 1) {
            change2Eng();
            return;
        }
        if (i == 2) {
            change2Fra();
        } else if (i == 3) {
            change2Span();
        } else {
            if (i != 4) {
                return;
            }
            change2Helan();
        }
    }

    public void change2Cn() {
        this.strNoSpace = this.context.getString(R.string.free_space_not_enough_cn);
        this.strNoConnect = this.context.getString(R.string.please_connect_wifi_cn);
        this.strNoSupport = this.context.getString(R.string.not_supported_change_cn);
        this.strConnected = this.context.getString(R.string.success_connect_wifi_cn);
        this.strDeleteTitle = this.context.getString(R.string.alart_title_txt_cn);
        this.strDeleteContent = "是否删除文件？";
        this.strDeleteConfirm = this.context.getString(R.string.alart_confirm_txt_cn);
        this.strDeleteCancel = this.context.getString(R.string.alart_cancel_txt_cn);
        this.strRecording = this.context.getString(R.string.play_hide_screen_rec_cn);
        this.strGsensorEnable = this.context.getString(R.string.play_hide_screen_gsensor_cn);
        this.strVRState = this.context.getString(R.string.play_hide_screen_Splite_cn);
        this.strPullUp = this.context.getString(R.string.please_power_on_helicope_cn);
        this.strPullDown = this.context.getString(R.string.please_power_down_helicope_cn);
        this.strGsnsorNo = this.context.getString(R.string.gsensor_not_support_cn);
        this.strRelFinger = this.context.getString(R.string.play_hide_screen_rudder_cn);
        this.strEncoding = "正在上传视频...";
        this.strUploadTitle = "上传文件";
        this.strUploadContent = "是否放弃上传文件？";
        this.strSelect = this.context.getString(R.string.select_music_cn);
        this.strCloseMusic = this.context.getString(R.string.close_music_cn);
        this.strConfirm = this.context.getString(R.string.confirm_music_cn);
        this.strPopularMusic = this.context.getString(R.string.popular_music_cn);
        this.strLocalMusic = this.context.getString(R.string.local_music_cn);
        this.strSnap = this.context.getString(R.string.start_snap_cn);
        this.strRecordStart = this.context.getString(R.string.start_record_cn);
        this.strRecordEnd = this.context.getString(R.string.finish_record_cn);
        this.strPermissionRequired = "需要存储权限才能访问本地音乐";
        this.strPermissionGranted = "权限已授予";
        this.strPermissionDenied = "权限被拒绝，无法加载本地音乐";
    }

    public void change2Eng() {
        this.strNoSpace = this.context.getString(R.string.free_space_not_enough_en);
        this.strNoConnect = this.context.getString(R.string.please_connect_wifi_en);
        this.strNoSupport = this.context.getString(R.string.not_supported_change_en);
        this.strConnected = this.context.getString(R.string.success_connect_wifi_en);
        this.strDeleteTitle = this.context.getString(R.string.alart_title_txt_en);
        this.strDeleteContent = this.context.getString(R.string.alart_content_txt_en);
        this.strDeleteConfirm = this.context.getString(R.string.alart_confirm_txt_en);
        this.strDeleteCancel = this.context.getString(R.string.alart_cancel_txt_en);
        this.strRecording = this.context.getString(R.string.play_hide_screen_rec_en);
        this.strGsensorEnable = this.context.getString(R.string.play_hide_screen_gsensor_en);
        this.strVRState = this.context.getString(R.string.play_hide_screen_Splite_en);
        this.strPullUp = this.context.getString(R.string.please_power_on_helicope_en);
        this.strPullDown = this.context.getString(R.string.please_power_down_helicope_en);
        this.strGsnsorNo = this.context.getString(R.string.gsensor_not_support_en);
        this.strLockDown = this.context.getString(R.string.lock_screen_click_dowon);
        this.strRelFinger = this.context.getString(R.string.play_hide_screen_rudder_en);
        this.strEncoding = "uploading...";
        this.strUploadTitle = "upload file";
        this.strUploadContent = "Do you give up uploading files?";
        this.strSelect = this.context.getString(R.string.select_music_en);
        this.strCloseMusic = this.context.getString(R.string.close_music_en);
        this.strConfirm = this.context.getString(R.string.confirm_music_en);
        this.strPopularMusic = this.context.getString(R.string.popular_music_en);
        this.strLocalMusic = this.context.getString(R.string.local_music_en);
        this.strSnap = this.context.getString(R.string.start_snap_en);
        this.strRecordStart = this.context.getString(R.string.start_record_en);
        this.strRecordEnd = this.context.getString(R.string.finish_record_en);
        this.strPermissionRequired = "Storage permission is required to access local music";
        this.strPermissionGranted = "Permission granted";
        this.strPermissionDenied = "Permission denied, cannot load local music";
    }

    public void change2Fra() {
        this.strNoSpace = this.context.getString(R.string.free_space_not_enough_fra);
        this.strNoConnect = this.context.getString(R.string.please_connect_wifi_fra);
        this.strConnected = this.context.getString(R.string.success_connect_wifi_fra);
        this.strDeleteTitle = this.context.getString(R.string.alart_title_txt_fra);
        this.strDeleteContent = this.context.getString(R.string.alart_content_txt_fra);
        this.strDeleteConfirm = this.context.getString(R.string.alart_confirm_txt_fra);
        this.strDeleteCancel = this.context.getString(R.string.alart_cancel_txt_fra);
        this.strRecording = this.context.getString(R.string.play_hide_screen_rec_fra);
        this.strGsensorEnable = this.context.getString(R.string.play_hide_screen_gsensor_fra);
        this.strVRState = this.context.getString(R.string.play_hide_screen_Splite_fra);
        this.strPullUp = this.context.getString(R.string.please_power_on_helicope_fra);
        this.strPullDown = this.context.getString(R.string.please_power_down_helicope_fra);
        this.strGsnsorNo = this.context.getString(R.string.gsensor_not_support_fra);
        this.strRelFinger = this.context.getString(R.string.play_hide_screen_rudder_fra);
        this.strEncoding = "uploading...";
        this.strUploadTitle = "upload file";
        this.strUploadContent = "Do you give up uploading files?";
    }

    public void change2Span() {
        this.strNoSpace = this.context.getString(R.string.free_space_not_enough_span);
        this.strNoConnect = this.context.getString(R.string.please_connect_wifi_span);
        this.strConnected = this.context.getString(R.string.success_connect_wifi_span);
        this.strDeleteTitle = this.context.getString(R.string.alart_title_txt_span);
        this.strDeleteContent = this.context.getString(R.string.alart_content_txt_span);
        this.strDeleteConfirm = this.context.getString(R.string.alart_confirm_txt_span);
        this.strDeleteCancel = this.context.getString(R.string.alart_cancel_txt_span);
        this.strRecording = this.context.getString(R.string.play_hide_screen_rec_span);
        this.strGsensorEnable = this.context.getString(R.string.play_hide_screen_gsensor_span);
        this.strVRState = this.context.getString(R.string.play_hide_screen_Splite_span);
        this.strPullUp = this.context.getString(R.string.please_power_on_helicope_span);
        this.strPullDown = this.context.getString(R.string.please_power_down_helicope_span);
        this.strGsnsorNo = this.context.getString(R.string.gsensor_not_support_span);
        this.strRelFinger = this.context.getString(R.string.play_hide_screen_rudder_span);
        this.strEncoding = "uploading...";
        this.strUploadTitle = "upload file";
        this.strUploadContent = "Do you give up uploading files?";
    }

    public void change2Helan() {
        this.strNoSpace = this.context.getString(R.string.free_space_not_enough_dutch);
        this.strNoConnect = this.context.getString(R.string.please_connect_wifi_dutch);
        this.strConnected = this.context.getString(R.string.success_connect_wifi_dutch);
        this.strDeleteTitle = this.context.getString(R.string.alart_title_txt_dutch);
        this.strDeleteContent = this.context.getString(R.string.alart_content_txt_dutch);
        this.strDeleteConfirm = this.context.getString(R.string.alart_confirm_txt_dutch);
        this.strDeleteCancel = this.context.getString(R.string.alart_cancel_txt_dutch);
        this.strRecording = this.context.getString(R.string.play_hide_screen_rec_dutch);
        this.strGsensorEnable = this.context.getString(R.string.play_hide_screen_gsensor_dutch);
        this.strVRState = this.context.getString(R.string.play_hide_screen_Splite_dutch);
        this.strPullUp = this.context.getString(R.string.please_power_on_helicope_dutch);
        this.strPullDown = this.context.getString(R.string.please_power_down_helicope_dutch);
        this.strGsnsorNo = this.context.getString(R.string.gsensor_not_support_dutch);
        this.strRelFinger = this.context.getString(R.string.play_hide_screen_rudder_dutch);
        this.strEncoding = "uploading...";
        this.strUploadTitle = "upload file";
        this.strUploadContent = "Do you give up uploading files?";
    }
}
