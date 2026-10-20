package com.weida.bot.wechatilinkbot.infrastructure.media;

/**
 * Indicates that local FFmpeg conversion failed.
 *
 * @author ryx
 * @since 2026-07-20
 */
public class MediaConversionException extends RuntimeException {

    public MediaConversionException(String message) {
        super(message);
    }

    public MediaConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
