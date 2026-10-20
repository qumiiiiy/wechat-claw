package com.weida.bot.wechatilinkbot.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of reading a trusted XLSX workbook through the capability.
 *
 * <p>The path is server-resolved when reading from disk; the capability
 * never accepts a path supplied by the model. In-memory reads (such as
 * the pre-validation step of an append operation) leave {@code sourcePath}
 * as {@code null} but still return a fully verified sheet summary.</p>
 *
 * @param sourcePath  server-controlled path to the file that was read,
 *                    or {@code null} when the bytes were not loaded from
 *                    a path (in-memory verification)
 * @param totalBytes  byte length of the file as it sat on disk when read
 * @param sheets      verified per-sheet view in workbook order
 */
public record SpreadsheetReadResult(
        Path sourcePath,
        long totalBytes,
        List<SpreadsheetSheetView> sheets) {

    public SpreadsheetReadResult {
        Objects.requireNonNull(sheets, "sheets 不能为空");
        sheets = List.copyOf(sheets);
        if (totalBytes < 0) {
            throw new IllegalArgumentException("totalBytes 不能为负数");
        }
    }
}