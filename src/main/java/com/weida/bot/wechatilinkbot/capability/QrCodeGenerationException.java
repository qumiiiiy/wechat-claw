package com.weida.bot.wechatilinkbot.capability;

/**
 * Indicates that QR code generation failed validation or execution.
 *
 * @author ryx
 * @since 2026-07-24
 */
public class QrCodeGenerationException extends RuntimeException {

    public QrCodeGenerationException(String message) {
        super(message);
    }

    public QrCodeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
