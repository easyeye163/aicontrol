package com.aicontrol.android.aircam.bean;

/* loaded from: classes5.dex */
public class MessageWrap {
    public final String message;

    public static MessageWrap getInstance(String str) {
        return new MessageWrap(str);
    }

    private MessageWrap(String str) {
        this.message = str;
    }
}
