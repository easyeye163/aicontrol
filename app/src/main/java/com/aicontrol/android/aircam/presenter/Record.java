package com.aicontrol.android.aircam.presenter;

import android.content.Context;
import android.graphics.Bitmap;
import com.aicontrol.android.aircam.utils.Camera;
import com.aicontrol.android.aircam.utils.LogUtils;
import com.aicontrol.android.aircam.utils.PathUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/* loaded from: classes5.dex */
public class Record {
    private Context mContext;
    private PathUtils mPathUtils;
    private File photoFile = null;
    LogUtils logUtils = LogUtils.setLogger(Record.class);
    private boolean bPhotoSnap = false;

    public Record(Context context) {
        this.mContext = null;
        this.mPathUtils = null;
        this.mContext = context;
        this.mPathUtils = new PathUtils(this.mContext);
    }

    public void prepareRecord(boolean z) {
        try {
            String createVideoFile = this.mPathUtils.createVideoFile(z);
            File file = new File(createVideoFile.replace(".dat", ".jpg").replace("/video/", "/.videoPic/"));
            this.photoFile = file;
            if (!file.exists()) {
                this.photoFile.createNewFile();
            }
            Camera.iCameraRecSetParams(640, 480, 25);
            Camera.iCameraRecStart(createVideoFile);
            this.bPhotoSnap = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void takeSnap(final Bitmap bitmap) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (Record.this.photoFile != null && Record.this.bPhotoSnap) {
                    try {
                        FileOutputStream fileOutputStream = new FileOutputStream(Record.this.photoFile);
                        Record.this.logUtils.e("width:" + bitmap.getWidth() + " height:" + bitmap.getHeight());
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
                        fileOutputStream.flush();
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Record.this.bPhotoSnap = false;
                }
            }
        }).start();
    }
}
