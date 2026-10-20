package com.weida.bot.wechatilinkbot.infrastructure.learning;

/** Stable failure raised while starting or checking a Chaoxing QR login. */
public class ChaoxingLoginException extends RuntimeException {

    public ChaoxingLoginException(String message) {
        super(message);
    }

    public ChaoxingLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
