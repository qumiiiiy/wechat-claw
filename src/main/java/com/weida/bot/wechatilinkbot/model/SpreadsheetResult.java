package com.weida.bot.wechatilinkbot.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of a successful spreadsheet generation.
 *
 * <p>The capability verifies the on-disk workbook by reopening it and
 * inspecting the sheet name, headers, and row count; this record returns
 * those confirmed values so callers (tools, tests, or future agents) can
 * trust that the bytes on disk match what was requested.</p>
 *
 * @param filePath       server-controlled path inside the configured output
 *                       directory; never equals a path the caller provided
 * @param bytes          generated workbook bytes (defensive copy inside the
 *                       record constructor)
 * @param sheetName      sheet name actually verified on disk
 * @param verifiedHeaders header cells verified on disk
 * @param verifiedRowCount number of data rows verified on disk (excludes
 *                       the header row)
 */
public record SpreadsheetResult(
        Path filePath,
        byte[] bytes,
        String sheetName,
        List<String> verifiedHeaders,
        int verifiedRowCount) {

    public SpreadsheetResult {
        Objects.requireNonNull(filePath, "filePath 不能为空");
        Objects.requireNonNull(sheetName, "sheetName 不能为空");
        Objects.requireNonNull(verifiedHeaders, "verifiedHeaders 不能为空");
        if (verifiedRowCount < 0) {
            throw new IllegalArgumentException("verifiedRowCount 不能为负数");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes 不能为空");
        }
        bytes = bytes.clone();
        verifiedHeaders = List.copyOf(verifiedHeaders);
    }

    public byte[] bytes() {
        return bytes.clone();
    }
}
