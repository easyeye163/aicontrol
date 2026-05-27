package com.aicontrol.android.aircam.model.yuan;

/* loaded from: classes5.dex */
public class FileUtils {
    public static final String FILE_EXTENSION_SEPARATOR = ".";

    private FileUtils() {
        throw new AssertionError();
    }

    /* JADX WARN: Removed duplicated region for block: B:42:0x0066 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:49:? A[SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:50:0x005c A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:60:0x0055 -> B:18:0x0058). Please report as a decompilation issue!!! */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static void createFileWithByte(byte[] r3, java.lang.String r4) {
        /*
            java.io.File r0 = new java.io.File
            r0.<init>(r4)
            r4 = 0
            boolean r1 = r0.exists()     // Catch: java.lang.Throwable -> L3c java.lang.Exception -> L3f
            if (r1 != 0) goto L13
            boolean r1 = r0.createNewFile()     // Catch: java.lang.Throwable -> L3c java.lang.Exception -> L3f
            if (r1 != 0) goto L13
            return
        L13:
            java.io.FileOutputStream r1 = new java.io.FileOutputStream     // Catch: java.lang.Throwable -> L3c java.lang.Exception -> L3f
            r2 = 1
            r1.<init>(r0, r2)     // Catch: java.lang.Throwable -> L3c java.lang.Exception -> L3f
            java.io.BufferedOutputStream r0 = new java.io.BufferedOutputStream     // Catch: java.lang.Throwable -> L34 java.lang.Exception -> L38
            r0.<init>(r1)     // Catch: java.lang.Throwable -> L34 java.lang.Exception -> L38
            r0.write(r3)     // Catch: java.lang.Throwable -> L30 java.lang.Exception -> L32
            r0.flush()     // Catch: java.lang.Throwable -> L30 java.lang.Exception -> L32
            r1.close()     // Catch: java.io.IOException -> L28
            goto L2c
        L28:
            r3 = move-exception
            r3.printStackTrace()
        L2c:
            r0.close()     // Catch: java.lang.Exception -> L54
            goto L58
        L30:
            r3 = move-exception
            goto L36
        L32:
            r3 = move-exception
            goto L3a
        L34:
            r3 = move-exception
            r0 = r4
        L36:
            r4 = r1
            goto L5a
        L38:
            r3 = move-exception
            r0 = r4
        L3a:
            r4 = r1
            goto L41
        L3c:
            r3 = move-exception
            r0 = r4
            goto L5a
        L3f:
            r3 = move-exception
            r0 = r4
        L41:
            r3.printStackTrace()     // Catch: java.lang.Throwable -> L59
            if (r4 == 0) goto L4e
            r4.close()     // Catch: java.io.IOException -> L4a
            goto L4e
        L4a:
            r3 = move-exception
            r3.printStackTrace()
        L4e:
            if (r0 == 0) goto L58
            r0.close()     // Catch: java.lang.Exception -> L54
            goto L58
        L54:
            r3 = move-exception
            r3.printStackTrace()
        L58:
            return
        L59:
            r3 = move-exception
        L5a:
            if (r4 == 0) goto L64
            r4.close()     // Catch: java.io.IOException -> L60
            goto L64
        L60:
            r4 = move-exception
            r4.printStackTrace()
        L64:
            if (r0 == 0) goto L6e
            r0.close()     // Catch: java.lang.Exception -> L6a
            goto L6e
        L6a:
            r4 = move-exception
            r4.printStackTrace()
        L6e:
            throw r3
        */
        throw new UnsupportedOperationException("Method not decompiled: com.yuan.FileUtils.createFileWithByte(byte[], java.lang.String):void");
    }

    /* JADX WARN: Removed duplicated region for block: B:40:0x0062 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:47:? A[SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:48:0x0058 A[EXC_TOP_SPLITTER, SYNTHETIC] */
    /* JADX WARN: Unsupported multi-entry loop pattern (BACK_EDGE: B:58:0x0051 -> B:15:0x0054). Please report as a decompilation issue!!! */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static void createFileWithByte(byte[] r3, int r4, int r5, java.lang.String r6) {
        /*
            java.io.File r0 = new java.io.File
            r0.<init>(r6)
            r6 = 0
            boolean r1 = r0.exists()     // Catch: java.lang.Throwable -> L38 java.lang.Exception -> L3b
            if (r1 != 0) goto Lf
            r0.createNewFile()     // Catch: java.lang.Throwable -> L38 java.lang.Exception -> L3b
        Lf:
            java.io.FileOutputStream r1 = new java.io.FileOutputStream     // Catch: java.lang.Throwable -> L38 java.lang.Exception -> L3b
            r2 = 1
            r1.<init>(r0, r2)     // Catch: java.lang.Throwable -> L38 java.lang.Exception -> L3b
            java.io.BufferedOutputStream r0 = new java.io.BufferedOutputStream     // Catch: java.lang.Throwable -> L30 java.lang.Exception -> L34
            r0.<init>(r1)     // Catch: java.lang.Throwable -> L30 java.lang.Exception -> L34
            r0.write(r3, r4, r5)     // Catch: java.lang.Throwable -> L2c java.lang.Exception -> L2e
            r0.flush()     // Catch: java.lang.Throwable -> L2c java.lang.Exception -> L2e
            r1.close()     // Catch: java.io.IOException -> L24
            goto L28
        L24:
            r3 = move-exception
            r3.printStackTrace()
        L28:
            r0.close()     // Catch: java.lang.Exception -> L50
            goto L54
        L2c:
            r3 = move-exception
            goto L32
        L2e:
            r3 = move-exception
            goto L36
        L30:
            r3 = move-exception
            r0 = r6
        L32:
            r6 = r1
            goto L56
        L34:
            r3 = move-exception
            r0 = r6
        L36:
            r6 = r1
            goto L3d
        L38:
            r3 = move-exception
            r0 = r6
            goto L56
        L3b:
            r3 = move-exception
            r0 = r6
        L3d:
            r3.printStackTrace()     // Catch: java.lang.Throwable -> L55
            if (r6 == 0) goto L4a
            r6.close()     // Catch: java.io.IOException -> L46
            goto L4a
        L46:
            r3 = move-exception
            r3.printStackTrace()
        L4a:
            if (r0 == 0) goto L54
            r0.close()     // Catch: java.lang.Exception -> L50
            goto L54
        L50:
            r3 = move-exception
            r3.printStackTrace()
        L54:
            return
        L55:
            r3 = move-exception
        L56:
            if (r6 == 0) goto L60
            r6.close()     // Catch: java.io.IOException -> L5c
            goto L60
        L5c:
            r4 = move-exception
            r4.printStackTrace()
        L60:
            if (r0 == 0) goto L6a
            r0.close()     // Catch: java.lang.Exception -> L66
            goto L6a
        L66:
            r4 = move-exception
            r4.printStackTrace()
        L6a:
            throw r3
        */
        throw new UnsupportedOperationException("Method not decompiled: com.yuan.FileUtils.createFileWithByte(byte[], int, int, java.lang.String):void");
    }
}
