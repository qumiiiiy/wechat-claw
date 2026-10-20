package com.weida.bot.wechatilinkbot.infrastructure.spreadsheet;

/**
 * Domain exception raised when reading an externally-sourced XLSX fails
 * validation, regardless of whether the bytes come from disk or memory.
 *
 * <p>Distinct from {@link SpreadsheetWriteException} so the tool layer can
 * translate read failures into a stable, caller-facing error type without
 * coupling to render-time concerns.</p>
 */
public class SpreadsheetReadException extends RuntimeException {

    public SpreadsheetReadException(String message) {
        super(message);
    }

    public SpreadsheetReadException(String message, Throwable cause) {
        super(message, cause);
    }
}