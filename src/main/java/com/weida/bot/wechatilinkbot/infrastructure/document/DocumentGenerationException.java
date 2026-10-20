package com.weida.bot.wechatilinkbot.infrastructure.document;

/**
 * Raised when DOCX rendering, validation, or atomic write fails. The
 * underlying cause is preserved for diagnostics but the public message
 * intentionally omits file paths, document content, and full POI stack
 * traces.
 */
public class DocumentGenerationException extends RuntimeException {

    public DocumentGenerationException(String message) {
        super(message);
    }

    public DocumentGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}