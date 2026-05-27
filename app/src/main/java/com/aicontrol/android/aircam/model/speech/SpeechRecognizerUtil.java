package com.aicontrol.android.aircam.model.speech;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.IOException;

// TODO: PocketSphinx voice control disabled - entire class stubbed out
/* loaded from: classes5.dex */
public class SpeechRecognizerUtil {
    private static final String KWS_SEARCH = "wakeup";
    private boolean isLoad = false;
    private boolean isRunning = false;
    private File parentFile;

    public SpeechRecognizerUtil(Context context, Object recognitionListener) {
        // TODO: PocketSphinx voice control disabled
        this.parentFile = context.getFilesDir();
    }

    public void switchSearch() {
        // TODO: PocketSphinx voice control disabled
        Log.i("SpeechRecognizerUtil", "switchSearch - disabled");
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public boolean isLoad() {
        return this.isLoad;
    }

    public void stop() {
        this.isRunning = false;
    }

    public void shutdown() {
        this.isLoad = false;
        this.isRunning = false;
    }

    public void setupRecognizer(boolean z) throws IOException {
        // TODO: PocketSphinx voice control disabled
        this.isLoad = true;
    }
}
