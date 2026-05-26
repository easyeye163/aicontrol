package com.aicontrol.android.channel.qqbot;

public class QBotException extends Exception {
    public QBotException(String message) {
        super(message);
    }
    
    public QBotException(String message, Throwable cause) {
        super(message, cause);
    }
}
