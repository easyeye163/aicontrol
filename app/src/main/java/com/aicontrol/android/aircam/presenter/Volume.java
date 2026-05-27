package com.aicontrol.android.aircam.presenter;

import android.content.Context;
import android.media.AudioManager;

/* loaded from: classes5.dex */
public class Volume {
    private int MaxVoice;
    private Context context;
    private int curVoice;
    private AudioManager mAudioManager;

    public Volume(Context context) {
        this.curVoice = 0;
        this.MaxVoice = 0;
        this.mAudioManager = null;
        this.context = context;
        AudioManager audioManager = (AudioManager) context.getSystemService("audio");
        this.mAudioManager = audioManager;
        this.MaxVoice = audioManager.getStreamMaxVolume(3);
        this.curVoice = this.mAudioManager.getStreamVolume(3);
    }

    public int getCurVol() {
        return this.mAudioManager.getStreamVolume(3);
    }

    public int getMaxVol() {
        return this.MaxVoice;
    }

    public void setCurVol(int i) {
        this.curVoice = i;
        this.mAudioManager.setStreamVolume(3, i, 0);
    }
}
