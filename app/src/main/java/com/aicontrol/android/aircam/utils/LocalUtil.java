package com.aicontrol.android.aircam.utils;

import android.content.Context;

/* loaded from: classes5.dex */
public class LocalUtil {
    public static boolean isZh(Context context) {
        return context.getResources().getConfiguration().locale.getLanguage().endsWith("zh");
    }
}
