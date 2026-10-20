package com.weida.bot.wechatilinkbot.persistence;

/** Raised when persisted student todo state cannot be read or updated. */
public class StudentTodoPersistenceException extends RuntimeException {

    public StudentTodoPersistenceException(String message) {
        super(message);
    }

    public StudentTodoPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
