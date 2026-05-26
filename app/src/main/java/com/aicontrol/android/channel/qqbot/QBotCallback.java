package com.aicontrol.android.channel.qqbot;

public interface QBotCallback<T> {
    void onSuccess(T result);
    void onFailure(QBotException e);
}
