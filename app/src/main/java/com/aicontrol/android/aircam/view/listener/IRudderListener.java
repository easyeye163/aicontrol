package com.aicontrol.android.aircam.view.listener;

import com.aicontrol.android.aircam.view.pathdraw.PathPoint;
import java.util.Collection;

/* loaded from: classes5.dex */
public interface IRudderListener {
    void closeVoiceControl();

    void onAccNotify(int i, int i2);

    void onDirNotify(int i, int i2, int i3);

    void onPathFollowNotify(boolean z, Collection<PathPoint> collection);
}
