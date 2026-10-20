package com.weida.bot.wechatilinkbot.infrastructure.document;

/**
 * Raised when a generated DOCX cannot be re-read and re-verified after the
 * initial write. The temp file is deleted before this exception escapes the
 * capability boundary.
 */
public class DocumentReadException extends RuntimeException {

    public DocumentReadException(String message) {
        super(message);
    }

    public DocumentReadException(String message, Throwable cause) {
        super(message, cause);
    }
}