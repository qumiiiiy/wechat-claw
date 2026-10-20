package com.weida.bot.wechatilinkbot.infrastructure.mail;

/**
 * Sanitized infrastructure failure raised by the QQ SMTP adapter.
 */
public class QqMailDeliveryException extends RuntimeException {

    public QqMailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
