package com.weida.bot.wechatilinkbot.infrastructure.spreadsheet;

/**
 * Domain exception raised when spreadsheet generation or verification fails.
 *
 * <p>Root causes always come from invalid input, exhausted limits, or POI
 * failures; the cause chain is preserved for diagnostics but the message
 * never echoes raw cell content or file paths beyond what the caller
 * already supplied.</p>
 */
public class SpreadsheetWriteException extends RuntimeException {

    public SpreadsheetWriteException(String message) {
        super(message);
    }

    public SpreadsheetWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
