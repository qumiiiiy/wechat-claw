package com.weida.bot.wechatilinkbot.capability;

/** Signals that model-backed email classification cannot be completed safely. */
public class EmailClassificationUnavailableException extends RuntimeException {

    public EmailClassificationUnavailableException(String message) {
        super(message);
    }

    public EmailClassificationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
