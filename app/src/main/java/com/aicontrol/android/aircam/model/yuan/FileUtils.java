package com.aicontrol.android.aircam.model.yuan;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/* loaded from: classes5.dex */
public class FileUtils {
    public static final String FILE_EXTENSION_SEPARATOR = ".";

    private FileUtils() {
        throw new AssertionError();
    }

    public static void createFileWithByte(byte[] data, String path) {
        File file = new File(path);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        try {
            fos = new FileOutputStream(file, true);
            bos = new BufferedOutputStream(fos);
            bos.write(data);
            bos.flush();
            fos.close();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
            try { if (bos != null) bos.close(); } catch (IOException ex) { ex.printStackTrace(); }
            try { if (fos != null) fos.close(); } catch (IOException ex) { ex.printStackTrace(); }
        }
    }

    public static void createFileWithByte(byte[] data, int offset, int length, String path) {
        File file = new File(path);
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); return; }
        }
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        try {
            fos = new FileOutputStream(file, true);
            bos = new BufferedOutputStream(fos);
            bos.write(data, offset, length);
            bos.flush();
            fos.close();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
            try { if (bos != null) bos.close(); } catch (IOException ex) { ex.printStackTrace(); }
            try { if (fos != null) fos.close(); } catch (IOException ex) { ex.printStackTrace(); }
        }
    }
}
