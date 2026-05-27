package com.aicontrol.android.aircam.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import com.aicontrol.android.R;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;

/* loaded from: classes5.dex */
public class PathUtils {
    private static PathUtils INSTANCE = null;
    private static boolean debug = true;
    private Context context;
    private String mainDirName;
    private File photoDir = null;
    private File videoDir = null;
    private File videoPicDir = null;
    public File vrFile;

    public PathUtils(Context context) {
        this.mainDirName = "";
        this.context = context;
        this.mainDirName = "DCIM/" + context.getResources().getString(R.string.app_name);
        initFile();
    }

    public static PathUtils getInstance(Context context) {
        PathUtils pathUtils;
        synchronized (PathUtils.class) {
            if (INSTANCE == null) {
                INSTANCE = new PathUtils(context);
            }
            pathUtils = INSTANCE;
        }
        return pathUtils;
    }

    private void initFile() {
        File file = new File(Environment.getExternalStorageDirectory(), this.mainDirName);
        this.vrFile = file;
        if (!file.exists()) {
            this.vrFile.mkdirs();
        }
        File file2 = new File(this.vrFile, "photo");
        this.photoDir = file2;
        if (!file2.exists()) {
            this.photoDir.mkdirs();
        }
        File file3 = new File(this.vrFile, "video");
        this.videoDir = file3;
        if (!file3.exists()) {
            this.videoDir.mkdirs();
        }
        File file4 = new File(this.vrFile, ".videoPic");
        this.videoPicDir = file4;
        if (file4.exists()) {
            return;
        }
        this.videoPicDir.mkdirs();
    }

    public File createPhotoFile() throws IOException {
        File file = new File(this.photoDir, new SimpleDateFormat("MMddHHmmssSSS").format(Long.valueOf(System.currentTimeMillis())).concat(".jpg"));
        if (!file.exists()) {
            file.createNewFile();
        }
        return file;
    }

    public String createVideoFile(boolean z) throws IOException {
        File file = new File(this.videoDir, new SimpleDateFormat("yyyyMMddHHmmss").format(Long.valueOf(System.currentTimeMillis())).concat(".mp4"));
        if (!file.exists()) {
            file.createNewFile();
        }
        return file.getAbsolutePath();
    }

    public File getPhotoFile() {
        return this.photoDir;
    }

    public File getVideoFile() {
        return this.videoDir;
    }

    public String copyFilesFromAssets(String str) {
        String str2 = "";
        File filesDir = this.context.getFilesDir();
        String[] fileList = this.context.fileList();
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(filesDir);
        stringBuffer.append(File.separator);
        int i = 0;
        while (true) {
            if (i >= fileList.length) {
                i = -1;
                break;
            }
            if (fileList[i].contains(str)) {
                break;
            }
            i++;
        }
        if (i < 0) {
            try {
                String[] list = this.context.getAssets().list("");
                int i2 = 0;
                while (true) {
                    if (i2 >= list.length) {
                        break;
                    }
                    if (list[i2].equals(str)) {
                        str2 = list[i2];
                        break;
                    }
                    i2++;
                }
                InputStream open = this.context.getAssets().open(str2);
                FileOutputStream openFileOutput = this.context.openFileOutput(str, 0);
                byte[] bArr = new byte[1024];
                while (true) {
                    int read = open.read(bArr);
                    if (read != -1) {
                        openFileOutput.write(bArr, 0, read);
                    } else {
                        openFileOutput.flush();
                        open.close();
                        openFileOutput.close();
                        return stringBuffer.append(str).toString();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (!debug) {
                    return null;
                }
                Log.d("PathUtils", "Exception:" + e.toString());
                return null;
            }
        } else {
            return stringBuffer.append(fileList[i]).toString();
        }
    }
}
