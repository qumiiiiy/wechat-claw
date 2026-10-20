package com.weida.bot.wechatilinkbot.model;

import java.util.List;
import java.util.Objects;

/**
 * Read-only view of a single sheet inside a verified XLSX workbook.
 *
 * <p>The renderer reads sheets with strict limits on size, sheet count,
 * cell types, and prohibited features (macros, external links, formulas);
 * every value returned here is therefore guaranteed to be safe text that
 * was already validated against {@code SpreadsheetProperties}.</p>
 *
 * @param name     validated sheet name (non-blank, within Excel's 31-char limit)
 * @param headers  header row values, in column order, deduplicated
 * @param rowCount number of data rows excluding the header row
 */
public record SpreadsheetSheetView(
        String name,
        List<String> headers,
        int rowCount) {

    public SpreadsheetSheetView {
        Objects.requireNonNull(name, "sheet name 不能为空");
        if (name.isBlank()) {
            throw new IllegalArgumentException("sheet name 不能为空");
        }
        Objects.requireNonNull(headers, "headers 不能为空");
        headers = List.copyOf(headers);
        if (rowCount < 0) {
            throw new IllegalArgumentException("rowCount 不能为负数");
        }
    }
}