package com.weida.bot.wechatilinkbot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Request describing the second sheet that should be appended to a copy of
 * an existing XLSX workbook.
 *
 * <p>Used by {@code SpreadsheetCapability.appendSheet}. The capability owns
 * both the source path (it never comes from the model) and the destination
 * path (controlled filename inside the output directory).</p>
 *
 * <p>The new sheet must have a unique name within the resulting workbook;
 * sheet-name rules mirror Excel's 31-character limit and POI's
 * validation. Plain-string rows and typed rows are mutually exclusive —
 * exactly one of {@link #rows()} or {@link #typedRows()} must be
 * non-null.</p>
 *
 * @param newSheetName required, validated, max 31 chars
 * @param columns      ordered column headers (non-empty, deduplicated)
 * @param rows         data rows of plain strings; mutually exclusive with
 *                     {@code typedRows}
 * @param typedRows    data rows of typed values; mutually exclusive with
 *                     {@code rows}
 */
public record SpreadsheetAppendRequest(
        String newSheetName,
        List<SpreadsheetColumn> columns,
        List<List<String>> rows,
        List<List<SpreadsheetCellValue>> typedRows) {

    public SpreadsheetAppendRequest {
        Objects.requireNonNull(newSheetName, "newSheetName 不能为空");
        newSheetName = newSheetName.strip();
        if (newSheetName.isEmpty()) {
            throw new IllegalArgumentException("newSheetName 不能为空");
        }
        if (newSheetName.length() > 31) {
            throw new IllegalArgumentException("newSheetName 不能超过 31 字符");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns 不能为空");
        }
        columns = List.copyOf(columns);
        rows = normalizeRows(rows);
        typedRows = normalizeTypedRows(typedRows);
        if (!rows.isEmpty() && !typedRows.isEmpty()) {
            throw new IllegalArgumentException(
                    "SpreadsheetAppendRequest 的 rows 与 typedRows 互斥");
        }
    }

    /** Legacy 3-arg constructor kept for backward compatibility. */
    public SpreadsheetAppendRequest(
            String newSheetName,
            List<SpreadsheetColumn> columns,
            List<List<String>> rows) {
        this(newSheetName, columns, rows, null);
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