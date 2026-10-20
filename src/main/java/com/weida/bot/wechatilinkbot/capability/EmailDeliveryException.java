package com.weida.bot.wechatilinkbot.capability;

/**
 * Indicates that an approved email could not be delivered through QQ SMTP.
 *
 * @author ryx
 * @since 2026-07-23
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
