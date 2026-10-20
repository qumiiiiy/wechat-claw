package com.weida.bot.wechatilinkbot.persistence;

/** Raised when persisted desktop task state cannot be read or updated. */
public class DesktopTaskPersistenceException extends RuntimeException {

    public DesktopTaskPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
