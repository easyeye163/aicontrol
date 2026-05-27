package com.aicontrol.android.aircam.model.listener;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

/* loaded from: classes5.dex */
public class AutoXml {
    private static final String HTemplate = "<dimen name=\"y{0}\">{1}px</dimen>\n";
    private static final String WTemplate = "<dimen name=\"x{0}\">{1}px</dimen>\n";
    private static AutoXml mInstance;
    private Context mContext;
    private File lay_x = null;
    private File lay_y = null;
    private int baseW = 1280;
    private int baseH = 720;

    public float change(float f) {
        return ((int) (f * 100.0f)) / 100.0f;
    }

    public AutoXml(Context context) {
        this.mContext = context;
    }

    public static AutoXml getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new AutoXml(context);
        }
        return mInstance;
    }

    public void generalXml(int i, int i2) {
        createFolder(i, i2);
        putXmlContent(i, i2);
    }

    public void createFolder(int i, int i2) {
        File file = new File(Environment.getExternalStorageDirectory(), "AutoXml");
        if (!file.exists()) {
            Toast.makeText(this.mContext, "create folder of WiFi_CAM", 1).show();
            file.mkdirs();
        }
        File file2 = new File(file, String.format("values-%dx%d", Integer.valueOf(i), Integer.valueOf(i2)));
        if (!file2.exists()) {
            Toast.makeText(this.mContext, "create folder of WiFi_CAM", 1).show();
            file2.mkdirs();
        }
        File file3 = new File(file2, "lay_x.xml");
        this.lay_x = file3;
        if (!file3.exists()) {
            try {
                this.lay_x.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        File file4 = new File(file2, "lay_y.xml");
        this.lay_y = file4;
        if (!file4.exists()) {
            try {
                this.lay_y.createNewFile();
            } catch (IOException e2) {
                e2.printStackTrace();
            }
        }
        Toast.makeText(this.mContext, "create folder or file success!\n", 1).show();
    }

    public void putXmlContent(int i, int i2) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>");
        float f = (i * 1.0f) / this.baseW;
        Log.e("AutoXml", "width : " + i + "," + this.baseW + "," + f);
        for (int i3 = 1; i3 < this.baseW; i3++) {
            stringBuffer.append(WTemplate.replace("{0}", i3 + "").replace("{1}", change(((float) i3) * f) + ""));
        }
        stringBuffer.append(WTemplate.replace("{0}", this.baseW + "").replace("{1}", i + ""));
        stringBuffer.append("</resources>");
        FileWriteBuffer(this.lay_x, stringBuffer);
        StringBuffer stringBuffer2 = new StringBuffer();
        stringBuffer2.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>");
        float f2 = (i2 * 1.0f) / this.baseH;
        Log.e("AutoXml", "height : " + i2 + "," + this.baseH + "," + f2);
        for (int i4 = 1; i4 < this.baseH; i4++) {
            stringBuffer2.append(HTemplate.replace("{0}", i4 + "").replace("{1}", change(((float) i4) * f2) + ""));
        }
        stringBuffer2.append(HTemplate.replace("{0}", this.baseH + "").replace("{1}", i2 + ""));
        stringBuffer2.append("</resources>");
        FileWriteBuffer(this.lay_y, stringBuffer2);
    }

    public void FileWriteBuffer(File file, StringBuffer stringBuffer) {
        try {
            PrintWriter printWriter = new PrintWriter(new FileOutputStream(file));
            printWriter.print(stringBuffer.toString());
            printWriter.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
