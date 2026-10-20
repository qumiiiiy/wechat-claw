package com.weida.bot.wechatilinkbot.infrastructure.pptx;

/**
 * Runtime exception for PPTX render / write failures. Mirrors the shape
 * of {@code DocumentGenerationException} so that capability-level error
 * handling stays uniform across document formats.
 */
public class PptxGenerationException extends RuntimeException {

    public PptxGenerationException(String message) {
        super(message);
    }

    public PptxGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}