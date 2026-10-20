package com.weida.bot.wechatilinkbot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Minimal bounded spreadsheet creation request.
 *
 * <p>The capability owns the output path; callers MUST NOT supply one. The
 * sheet name is optional and falls back to {@code "Sheet1"}. The renderer
 * accepts cells as plain strings (V1) or as typed
 * {@link SpreadsheetCellValue} entries (numeric, date, or whitelisted
 * formula). The two forms are mutually exclusive — exactly one of
 * {@link #rows()} or {@link #typedRows()} must be non-null.</p>
 *
 * @param title     required document title; also used to derive the safe
 *                  file basename on the server
 * @param sheetName optional sheet name; falls back to {@code "Sheet1"}
 *                  when blank
 * @param columns   ordered column headers (non-empty, deduplicated)
 * @param rows      data rows of plain strings; mutually exclusive with
 *                  {@code typedRows}
 * @param typedRows data rows of typed values; mutually exclusive with
 *                  {@code rows}
 */
public record SpreadsheetRequest(
        String title,
        String sheetName,
        List<SpreadsheetColumn> columns,
        List<List<String>> rows,
        List<List<SpreadsheetCellValue>> typedRows) {

    public SpreadsheetRequest {
        Objects.requireNonNull(title, "title 不能为空");
        title = title.strip();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title 不能为空");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns 不能为空");
        }
        columns = List.copyOf(columns);
        if (sheetName != null) {
            sheetName = sheetName.strip();
            if (sheetName.isEmpty()) {
                sheetName = null;
            }
        }
        rows = normalizeRows(rows);
        typedRows = normalizeTypedRows(typedRows);
        if (!rows.isEmpty() && !typedRows.isEmpty()) {
            throw new IllegalArgumentException(
                    "SpreadsheetRequest 的 rows 与 typedRows 互斥，只能指定其一");
        }
    }

    /**
     * Legacy 4-arg constructor kept for backward compatibility; delegates
     * to the canonical 5-arg form with {@code typedRows = null}.
     */
    public SpreadsheetRequest(
            String title,
            String sheetName,
            List<SpreadsheetColumn> columns,
            List<List<String>> rows) {
        this(title, sheetName, columns, rows, null);
    }

    private static List<List<String>> normalizeRows(List<List<String>> rows) {
        if (rows == null) {
            return List.of();
        }
        List<List<String>> copy = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            if (row == null) {
                copy.add(List.of());
            } else {
                copy.add(List.copyOf(row));
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<List<SpreadsheetCellValue>> normalizeTypedRows(
            List<List<SpreadsheetCellValue>> typedRows) {
        if (typedRows == null) {
            return List.of();
        }
        List<List<SpreadsheetCellValue>> copy = new ArrayList<>(typedRows.size());
        for (List<SpreadsheetCellValue> row : typedRows) {
            if (row == null) {
                copy.add(List.of());
            } else {
                copy.add(List.copyOf(row));
            }
        }
        return Collections.unmodifiableList(copy);
    }
}