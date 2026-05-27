package com.aicontrol.android.aircam.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.view.Window;
import android.view.WindowManager;
import com.aicontrol.android.aircam.model.yuan.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/* loaded from: classes5.dex */
public class AppUtils implements IConstants {
    private static final String TAG = "AppUtils";

    public static int adjustRtsResolution(int i, int i2) {
        if (i == 1920 && i2 == 1080) {
            return 2;
        }
        return (i == 640 && i2 == 480) ? 0 : 1;
    }

    public static int[] getRtsResolution(int i) {
        int i2;
        int i3;
        int[] iArr = new int[2];
        if (2 == i) {
            i2 = 1920;
            i3 = 1080;
        } else if (i == 0) {
            i2 = 640;
            i3 = 368;
        } else {
            i2 = 1280;
            i3 = 720;
        }
        iArr[0] = i2;
        iArr[1] = i3;
        return iArr;
    }

    public static boolean isAppInBackground(Context context) {
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = ((ActivityManager) context.getSystemService("activity")).getRunningAppProcesses();
        boolean z = true;
        if (runningAppProcesses == null) {
            return true;
        }
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcesses) {
            if (runningAppProcessInfo.importance == 100) {
                for (String str : runningAppProcessInfo.pkgList) {
                    if (str.equals(context.getPackageName())) {
                        z = false;
                    }
                }
            }
        }
        return z;
    }

    public static int dip2px(Context context, float f) {
        return (int) ((f * context.getResources().getDisplayMetrics().density) + 0.5f);
    }

    public static int getScreenWidth(Context context) {
        return ((WindowManager) context.getSystemService("window")).getDefaultDisplay().getWidth();
    }

    public static String splicingFilePath(String str, String str2, String str3, String str4) {
        if (TextUtils.isEmpty(str)) {
            return ROOT_PATH;
        }
        String str5 = ROOT_PATH;
        if (str.contains(File.separator)) {
            for (String str6 : str.split(File.separator)) {
                if (!TextUtils.isEmpty(str6)) {
                    str5 = str5 + File.separator + str6;
                    File file = new File(str5);
                    if (!file.exists()) {
                        file.mkdir();
                    }
                }
            }
        } else {
            str5 = str5 + File.separator + str;
            File file2 = new File(str5);
            if (!file2.exists()) {
                file2.mkdir();
            }
        }
        if (TextUtils.isEmpty(str2)) {
            return str5;
        }
        String str7 = str5 + File.separator + str2;
        File file3 = new File(str7);
        if (!file3.exists()) {
            file3.mkdir();
        }
        if (TextUtils.isEmpty(str3)) {
            return str7;
        }
        String str8 = str7 + File.separator + str3;
        File file4 = new File(str8);
        if (!file4.exists()) {
            file4.mkdir();
        }
        if (TextUtils.isEmpty(str4)) {
            return str8;
        }
        String str9 = str8 + File.separator + str4;
        File file5 = new File(str9);
        if (!file5.exists()) {
            file5.mkdir();
        }
        return str9;
    }

    public static void deleteFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            if (file.delete()) {
                System.out.printf("delete file success!", new Object[0]);
                return;
            }
            return;
        }
        if (file.isDirectory()) {
            File[] listFiles = file.listFiles();
            if (listFiles != null && listFiles.length != 0) {
                for (File file2 : listFiles) {
                    deleteFile(file2);
                }
                if (file.delete()) {
                    System.out.printf("delete empty file success!", new Object[0]);
                    return;
                }
                return;
            }
            if (file.delete()) {
                System.out.printf("delete empty file success!", new Object[0]);
            }
        }
    }

    public static int judgeFileType(String str) {
        if (TextUtils.isEmpty(str)) {
            return 0;
        }
        if (str.endsWith(".png") || str.endsWith(".PNG") || str.endsWith(".JPEG") || str.endsWith(".jpeg") || str.endsWith(".jpg") || str.endsWith(".JPG")) {
            return 1;
        }
        return (str.endsWith(".mov") || str.endsWith(".MOV") || str.endsWith(".mp4") || str.endsWith(".MP4") || str.endsWith(".avi") || str.endsWith(".AVI")) ? 2 : 0;
    }

    private static String[] sort(String[] strArr) {
        if (strArr == null || strArr.length == 0) {
            return strArr;
        }
        int length = strArr.length;
        double[] dArr = new double[length];
        for (int i = 0; i < length; i++) {
            try {
                dArr[i] = Double.valueOf(strArr[i]).doubleValue();
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return sort(dArr);
    }

    private static String[] sort(double[] dArr) {
        if (dArr == null || dArr.length == 0) {
            return null;
        }
        for (int i = 0; i < dArr.length; i++) {
            int i2 = 0;
            while (i2 < (dArr.length - i) - 1) {
                int i3 = i2 + 1;
                double d = dArr[i2];
                double d2 = dArr[i3];
                if (d < d2) {
                    dArr[i2] = d2;
                    dArr[i3] = d;
                }
                i2 = i3;
            }
        }
        int length = dArr.length;
        String[] strArr = new String[length];
        for (int i4 = 0; i4 < length; i4++) {
            strArr[i4] = String.valueOf(dArr[i4]);
        }
        return strArr;
    }

    private static String[] parseDeviceVersionInfo(String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        if (str.contains("\r")) {
            str = str.replace("\r", "");
        }
        if (str.contains("\n")) {
            str = str.replace("\n", "");
        }
        if (str.contains("\"")) {
            str = str.replace("\"", "");
        }
        if (str.contains(",")) {
            return str.split(",");
        }
        return new String[]{str};
    }

    public static String getFromRaw(Context context, int i) {
        if (context == null) {
            return "";
        }
        InputStream inputStream = null;
        try {
            inputStream = context.getResources().openRawResource(i);
            byte[] bArr = new byte[inputStream.available()];
            inputStream.read(bArr);
            String str = new String(bArr, "GBK");
            if (inputStream == null) {
                return str;
            }
            try {
                inputStream.close();
                return str;
            } catch (IOException e) {
                e.printStackTrace();
                return str;
            }
        } catch (Exception e2) {
            e2.printStackTrace();
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e3) {
                    e3.printStackTrace();
                }
            }
            return "";
        } catch (Throwable th) {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e4) {
                    e4.printStackTrace();
                }
            }
            throw th;
        }
    }

    public static List<FileInfo> getAllLocalFile(String str, String str2, boolean z) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        ArrayList arrayList = new ArrayList();
        try {
            File file = new File(str);
            if (file.exists() && file.isDirectory()) {
                for (File file2 : file.listFiles()) {
                    if (file2.isDirectory()) {
                        for (File file3 : file2.listFiles()) {
                            if (file3.getName().equals(str2)) {
                                arrayList.addAll(getLocalFileInfo(file3.getPath(), z));
                            }
                        }
                    } else {
                        FileInfo fileInfo = new FileInfo();
                        if (z) {
                            fileInfo.setFilename(getFileName(file2.getName()));
                            fileInfo.setDirectory(false);
                            fileInfo.setSize(file2.length());
                            fileInfo.setCreateDate(getFileCreateTime(file2.getName()));
                            fileInfo.setPath(file2.getAbsolutePath());
                            fileInfo.setFileType(IConstants.BROWSE_LOCAL_MODE);
                        } else {
                            String formatedDateTime = TimeFormater.getFormatedDateTime(TimeFormater.yyyyMMddHHmmss, file2.lastModified());
                            if (formatedDateTime == null) {
                                formatedDateTime = "2015-08-07 15:34:26";
                            }
                            fileInfo.setFilename(file2.getName());
                            fileInfo.setDirectory(file2.isDirectory());
                            fileInfo.setSize(file2.length());
                            fileInfo.setCreateDate(formatedDateTime);
                            fileInfo.setPath(file2.getAbsolutePath());
                            fileInfo.setFileType(IConstants.BROWSE_RECORD_MODE);
                        }
                        arrayList.add(fileInfo);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Collections.sort(arrayList, new Comparator<FileInfo>() { // from class: com.tzh.wifi.wificam.utils.AppUtils.1
            @Override // java.util.Comparator
            public int compare(FileInfo fileInfo2, FileInfo fileInfo3) {
                return fileInfo3.getFilename().compareTo(fileInfo2.getFilename());
            }
        });
        return arrayList;
    }

    public static List<FileInfo> getLocalFileInfo(String str, boolean z) {
        File file;
        ArrayList arrayList = new ArrayList();
        if (str != null && !str.isEmpty()) {
            try {
                File file2 = new File(str);
                if (file2.exists() && file2.isDirectory()) {
                    File[] listFiles = file2.listFiles();
                    HashMap hashMap = new HashMap();
                    ArrayList arrayList2 = new ArrayList();
                    if (listFiles != null && listFiles.length > 0) {
                        for (File file3 : listFiles) {
                            if (file3.isFile()) {
                                if (z) {
                                    String name = file3.getName();
                                    if (!TextUtils.isEmpty(name)) {
                                        arrayList2.add(name);
                                        hashMap.put(name, file3);
                                    }
                                } else {
                                    String str2 = file3.lastModified() + "";
                                    arrayList2.add(str2);
                                    hashMap.put(str2, file3);
                                }
                            }
                        }
                        for (String str3 : descSort((String[]) arrayList2.toArray(new String[arrayList2.size()]))) {
                            if (!TextUtils.isEmpty(str3) && (file = (File) hashMap.get(str3)) != null) {
                                FileInfo fileInfo = new FileInfo();
                                if (z) {
                                    if (file.isFile() && str3.equals(file.getName())) {
                                        fileInfo.setFilename(getFileName(file.getName()));
                                        fileInfo.setDirectory(false);
                                        fileInfo.setSize(file.length());
                                        fileInfo.setCreateDate(getFileCreateTime(file.getName()));
                                        fileInfo.setPath(file.getAbsolutePath());
                                        fileInfo.setFileType(IConstants.BROWSE_LOCAL_MODE);
                                    }
                                } else if (file.isFile()) {
                                    if (str3.equals(file.lastModified() + "")) {
                                        String formatedDateTime = TimeFormater.getFormatedDateTime(TimeFormater.yyyyMMddHHmmss, file.lastModified());
                                        if (formatedDateTime == null) {
                                            formatedDateTime = "2015-08-07 15:34:26";
                                        }
                                        fileInfo.setFilename(file.getName());
                                        fileInfo.setDirectory(false);
                                        fileInfo.setSize(file.length());
                                        fileInfo.setCreateDate(formatedDateTime);
                                        fileInfo.setPath(file.getAbsolutePath());
                                        fileInfo.setFileType(IConstants.BROWSE_RECORD_MODE);
                                    }
                                }
                                arrayList.add(fileInfo);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return arrayList;
    }

    private static String[] descSort(String[] strArr) {
        if (strArr != null && strArr.length != 0) {
            for (int i = 0; i < strArr.length; i++) {
                int i2 = 0;
                while (i2 < (strArr.length - i) - 1) {
                    int i3 = i2 + 1;
                    if (strArr[i2].compareTo(strArr[i3]) < 0) {
                        String str = strArr[i2];
                        strArr[i2] = strArr[i3];
                        strArr[i3] = str;
                    }
                    i2 = i3;
                }
            }
        }
        return strArr;
    }

    public static void descSort(List<String> list) {
        if (list == null || list.size() <= 0) {
            return;
        }
        Collections.sort(list, new Comparator<String>() { // from class: com.tzh.wifi.wificam.utils.AppUtils.2
            @Override // java.util.Comparator
            public int compare(String str, String str2) {
                if (str.compareTo(str2) > 0) {
                    return -1;
                }
                return str.compareTo(str2) < 0 ? 1 : 0;
            }
        });
    }

    private static String getFileName(String str) {
        if (!TextUtils.isEmpty(str)) {
            String substring = str.contains(FileUtils.FILE_EXTENSION_SEPARATOR) ? str.substring(str.indexOf(FileUtils.FILE_EXTENSION_SEPARATOR)) : "";
            String str2 = str.contains("_") ? str.split("_")[0] : null;
            if (!TextUtils.isEmpty(str2)) {
                return str2 + substring;
            }
        }
        return null;
    }

    private static String getFileCreateTime(String str) {
        if (TextUtils.isEmpty(str) || !str.contains("_")) {
            return null;
        }
        String[] split = str.split("_");
        if (split[1].length() < 14) {
            return null;
        }
        String str2 = split[1];
        return str2.substring(0, 4) + "-" + str2.substring(4, 6) + "-" + str2.substring(6, 8) + " " + str2.substring(8, 10) + ":" + str2.substring(10, 12) + ":" + str2.substring(12, 14);
    }

    public static boolean getRecordVideoThumb(FileInfo fileInfo, String str) {
        throw new UnsupportedOperationException("Method not decompiled: com.cooingdv.fhdfpv.utils.AppUtils.getRecordVideoThumb(com.cooingdv.fhdfpv.beans.FileInfo, java.lang.String):boolean");
    }

    public static Locale getLanguage(int i) {
        if (i == 0) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        if (i == 1) {
            return Locale.US;
        }
        return null;
    }

    public static void setLanguage(Context context, Locale locale) {
        Resources resources = context.getApplicationContext().getResources();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();
        Configuration configuration = resources.getConfiguration();
        if (locale != null) {
            configuration.setLocale(locale);
            if (Build.VERSION.SDK_INT >= 24) {
                AppUtils$$ExternalSyntheticApiModelOutline0.m();
                configuration.setLocales(AppUtils$$ExternalSyntheticApiModelOutline0.m(new Locale[]{locale}));
                context.createConfigurationContext(configuration);
            } else {
                Locale.setDefault(locale);
                resources.updateConfiguration(configuration, displayMetrics);
            }
        }
    }

    public static Context attachBaseContext(Context context) {
        return Build.VERSION.SDK_INT >= 24 ? updateResources(context) : context;
    }

    private static Context updateResources(Context context) {
        Resources resources = context.getResources();
        Locale language = getLanguage(PreferencesHelper.getSharedPreferences(context.getApplicationContext()).getInt(IConstants.KEY_LANGUAGE_FLAG, !LocalUtil.isZh(context) ? 1 : 0));
        Configuration configuration = resources.getConfiguration();
        configuration.setLocale(language);
        AppUtils$$ExternalSyntheticApiModelOutline0.m();
        configuration.setLocales(AppUtils$$ExternalSyntheticApiModelOutline0.m(new Locale[]{language}));
        return context.createConfigurationContext(configuration);
    }

    public static Bitmap reverseBitmap(Bitmap bitmap, int i) {
        Matrix matrix;
        if (bitmap == null) {
            return null;
        }
        Canvas canvas = new Canvas();
        Bitmap createBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        canvas.setBitmap(createBitmap);
        if (i == 0) {
            matrix = new Matrix();
            matrix.postScale(1.0f, -1.0f);
            matrix.postTranslate(0.0f, bitmap.getHeight());
        } else if (i != 1) {
            matrix = null;
        } else {
            matrix = new Matrix();
            matrix.postScale(-1.0f, 1.0f);
            matrix.postTranslate(bitmap.getWidth(), 0.0f);
        }
        if (matrix == null) {
            return bitmap;
        }
        canvas.drawBitmap(bitmap, matrix, null);
        return createBitmap;
    }

    public static Bitmap rotateBitmap(int i, Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(i);
        Bitmap createBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (createBitmap != bitmap && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        return createBitmap;
    }

    public static String getLocalPhotoName() {
        return "JPG_" + Calendar.getInstance().getTimeInMillis() + ".jpg";
    }

    public static boolean bytesToFile(byte[] bArr, String str) {
        throw new UnsupportedOperationException("Method not decompiled: com.cooingdv.fhdfpv.utils.AppUtils.bytesToFile(byte[], java.lang.String):boolean");
    }

    public static int checkFrameType(byte[] bArr) {
        byte b;
        if (bArr != null && bArr.length > 5) {
            byte[] bArr2 = new byte[5];
            System.arraycopy(bArr, 0, bArr2, 0, 5);
            if (bArr2[0] == 0 && bArr2[1] == 0) {
                byte b2 = bArr2[2];
                if (b2 != 1) {
                    return (b2 == 0 && bArr2[3] == 1 && (b = bArr2[4]) != 103 && b == 65) ? IConstants.FRAME_TYPE_P : IConstants.FRAME_TYPE_I;
                }
                byte b3 = bArr2[3];
                return (b3 == 103 || b3 != 65) ? IConstants.FRAME_TYPE_I : IConstants.FRAME_TYPE_P;
            }
        }
        return 0;
    }

    public static String getMediaDirectory(String str) {
        return (TextUtils.isEmpty(str) || !str.contains(IConstants.DEV_WORKSPACE_REAR)) ? IConstants.DIR_FRONT : IConstants.DIR_REAR;
    }

    public static long getAvailableExternalMemorySize() {
        StatFs statFs = new StatFs(Environment.getExternalStorageDirectory().getPath());
        return statFs.getBlockSizeLong() * statFs.getAvailableBlocksLong();
    }

    public static String getRecordVideoName() {
        return "REC_" + Calendar.getInstance().getTimeInMillis() + ".mov";
    }

    public static String formatUrl(String str, int i, String str2) {
        if (TextUtils.isEmpty(str) || i <= 0) {
            return null;
        }
        String str3 = "http://" + str + ":" + i + "/";
        if (TextUtils.isEmpty(str2)) {
            return str3;
        }
        return str3 + str2;
    }

    public static int parseThumbPathForDuration(String str) {
        if (TextUtils.isEmpty(str)) {
            return 0;
        }
        int lastIndexOf = str.lastIndexOf(FileUtils.FILE_EXTENSION_SEPARATOR);
        if (lastIndexOf != -1) {
            str = str.substring(0, lastIndexOf);
        }
        if (!str.contains("_")) {
            return 0;
        }
        String[] split = str.split("_");
        if (split.length <= 1) {
            return 0;
        }
        try {
            return Integer.valueOf(split[split.length - 1]).intValue();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static List<String> queryThumbDirPath(String str) {
        List<String> queryThumbDirPath;
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        File file = new File(str);
        if (!file.exists()) {
            return null;
        }
        ArrayList arrayList = new ArrayList();
        if (!file.isDirectory()) {
            return arrayList;
        }
        if (IConstants.DIR_THUMB.equals(file.getName())) {
            arrayList.add(file.getAbsolutePath());
            return arrayList;
        }
        File[] listFiles = file.listFiles();
        if (listFiles != null && listFiles.length > 0) {
            for (File file2 : listFiles) {
                if (file2.isDirectory() && (queryThumbDirPath = queryThumbDirPath(file2.getAbsolutePath())) != null && queryThumbDirPath.size() > 0) {
                    arrayList.addAll(queryThumbDirPath);
                }
            }
        }
        return arrayList;
    }

    public static boolean bitmapToFile(Bitmap bitmap, String str, int i) {
        throw new UnsupportedOperationException("Method not decompiled: com.cooingdv.fhdfpv.utils.AppUtils.bitmapToFile(android.graphics.Bitmap, java.lang.String, int):boolean");
    }

    public static int getCameraType(String str) {
        return (TextUtils.isEmpty(str) || !str.contains(IConstants.DEV_WORKSPACE_REAR)) ? 1 : 2;
    }

    public static long getFolderSize(File file) throws Exception {
        long length;
        long j = 0;
        if (file != null && file.exists()) {
            try {
                File[] listFiles = file.listFiles();
                if (listFiles != null && listFiles.length > 0) {
                    for (File file2 : listFiles) {
                        if (file2.isDirectory()) {
                            length = getFolderSize(file2);
                        } else {
                            length = file2.length();
                        }
                        j += length;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return j;
    }

    public static void setBrightness(Activity activity, int i) {
        Window window;
        WindowManager.LayoutParams attributes;
        if (activity == null || (window = activity.getWindow()) == null || (attributes = window.getAttributes()) == null) {
            return;
        }
        attributes.screenBrightness = i / 255.0f;
        window.setAttributes(attributes);
        Settings.System.putInt(activity.getContentResolver(), "screen_brightness", i);
    }

    public static int getScreenHeight(Context context) {
        return ((WindowManager) context.getSystemService("window")).getDefaultDisplay().getHeight();
    }

    public static String getSDTotalSize(Context context) {
        StatFs statFs = new StatFs(Environment.getExternalStorageDirectory().getPath());
        return Formatter.formatFileSize(context, statFs.getBlockSize() * statFs.getBlockCount());
    }

    public static String getSDAvailableSize(Context context) {
        StatFs statFs = new StatFs(Environment.getExternalStorageDirectory().getPath());
        return Formatter.formatFileSize(context, statFs.getBlockSize() * statFs.getAvailableBlocks());
    }

    private static String readTxtFile(String str) {
        throw new UnsupportedOperationException("Method not decompiled: com.cooingdv.fhdfpv.utils.AppUtils.readTxtFile(java.lang.String):java.lang.String");
    }

    public static String getAutoRearCameraKey(String str) {
        if (TextUtils.isEmpty(str)) {
            return "";
        }
        return str + "_rear_camera";
    }

    public static String createFilenameWidthTime(int i, String str) {
        long timeInMillis = Calendar.getInstance().getTimeInMillis();
        if (i != 1) {
            if (i == 2) {
                return "SOS_" + timeInMillis + str;
            }
            if (i != 3) {
                throw new IllegalArgumentException("Invalid type:" + i);
            }
        }
        return "REC_" + timeInMillis + str;
    }
}
