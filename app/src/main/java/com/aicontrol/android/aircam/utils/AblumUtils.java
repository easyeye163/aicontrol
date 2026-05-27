package com.aicontrol.android.aircam.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.File;

/* loaded from: classes5.dex */
public class AblumUtils {
    private static AblumUtils mInstance;
    private Context context;

    private AblumUtils(Context context) {
        this.context = context;
    }

    public static AblumUtils getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new AblumUtils(context);
        }
        return mInstance;
    }

    public void insertVideoToMediaStore(Context context, String str, long j, long j2) {
        insertVideoToMediaStore(context, str, j, 0, 0, j2);
    }

    public void insertImageToMediaStore(Context context, String str, long j) {
        insertImageToMediaStore(context, str, j, 0, 0);
    }

    public void scanFile(Context context, String str) {
        if (checkFile(str)) {
            Intent intent = new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
            intent.setData(Uri.fromFile(new File(str)));
            context.sendBroadcast(intent);
        }
    }

    private ContentValues initCommonContentValues(String str, long j) {
        ContentValues contentValues = new ContentValues();
        File file = new File(str);
        long timeWrap = getTimeWrap(j);
        contentValues.put("title", file.getName());
        contentValues.put("_display_name", file.getName());
        contentValues.put("date_modified", Long.valueOf(timeWrap));
        contentValues.put("date_added", Long.valueOf(timeWrap));
        contentValues.put("_data", file.getAbsolutePath());
        contentValues.put("_size", Long.valueOf(file.length()));
        return contentValues;
    }

    public void insertImageToMediaStore(Context context, String str, long j, int i, int i2) {
        if (checkFile(str)) {
            long timeWrap = getTimeWrap(j);
            ContentValues initCommonContentValues = initCommonContentValues(str, timeWrap);
            initCommonContentValues.put("datetaken", Long.valueOf(timeWrap));
            initCommonContentValues.put("orientation", (Integer) 0);
            initCommonContentValues.put("orientation", (Integer) 0);
            if (i > 0) {
                initCommonContentValues.put("width", (Integer) 0);
            }
            if (i2 > 0) {
                initCommonContentValues.put("height", (Integer) 0);
            }
            initCommonContentValues.put("mime_type", getPhotoMimeType(str));
            context.getApplicationContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, initCommonContentValues);
        }
    }

    public void insertVideoToMediaStore(Context context, String str, long j, int i, int i2, long j2) {
        String videoMimeType;
        if (checkFile(str) && (videoMimeType = getVideoMimeType(str)) != "unknow") {
            long timeWrap = getTimeWrap(j);
            ContentValues initCommonContentValues = initCommonContentValues(str, timeWrap);
            initCommonContentValues.put("datetaken", Long.valueOf(timeWrap));
            if (j2 > 0) {
                initCommonContentValues.put("duration", Long.valueOf(j2));
            }
            if (i > 0) {
                initCommonContentValues.put("width", Integer.valueOf(i));
            }
            if (i2 > 0) {
                initCommonContentValues.put("height", Integer.valueOf(i2));
            }
            initCommonContentValues.put("mime_type", videoMimeType);
            context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, initCommonContentValues);
        }
    }

    private boolean isSystemDcim(String str) {
        return str.toLowerCase().contains("dcim") || str.toLowerCase().contains("camera");
    }

    private String getPhotoMimeType(String str) {
        String lowerCase = str.toLowerCase();
        if (!lowerCase.endsWith("jpg") && !lowerCase.endsWith("jpeg")) {
            if (lowerCase.endsWith("png")) {
                return "image/png";
            }
            if (lowerCase.endsWith("gif")) {
                return "image/gif";
            }
        }
        return "image/jpeg";
    }

    private String getVideoMimeType(String str) {
        String lowerCase = str.toLowerCase();
        return (lowerCase.endsWith("mp4") || lowerCase.endsWith("mpeg4")) ? "video/mp4" : lowerCase.endsWith("3gp") ? "video/3gp" : "unknow";
    }

    private long getTimeWrap(long j) {
        return j <= 0 ? System.currentTimeMillis() : j;
    }

    private boolean checkFile(String str) {
        return new File(str).exists();
    }
}
