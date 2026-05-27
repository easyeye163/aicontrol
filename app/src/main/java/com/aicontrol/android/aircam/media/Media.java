package com.aicontrol.android.aircam.media;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import com.aicontrol.android.R;
import java.io.IOException;

/* loaded from: classes5.dex */
public class Media {
    private MediaPlayer mp;

    public Media(Context context) {
        this.mp = null;
        MediaPlayer create = MediaPlayer.create(context, R.raw.shutter);
        this.mp = create;
        try {
            create.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalStateException e2) {
            e2.printStackTrace();
        }
    }

    public void play() {
        new Thread(new Runnable() { // from class: com.tzh.wifi.wificam.media.Media.1
            @Override // java.lang.Runnable
            public void run() {
                try {
                    Media.this.mp.seekTo(0);
                    Media.this.mp.start();
                } catch (Exception e) {
                    Log.e("", "music error");
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void destroy() {
        MediaPlayer mediaPlayer = this.mp;
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }
}
