package com.weida.bot.wechatilinkbot.capability;

/**
 * Indicates that a paid media-generation request failed validation or execution.
 *
 * @author ryx
 * @since 2026-07-20
 */
public class MediaGenerationException extends RuntimeException {

    public MediaGenerationException(String message) {
        super(message);
    }

    public MediaGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
