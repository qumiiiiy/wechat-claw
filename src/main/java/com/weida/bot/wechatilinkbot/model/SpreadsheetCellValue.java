package com.weida.bot.wechatilinkbot.model;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Typed cell value for XLSX rendering. The {@link Kind} decides how
 * {@code SpreadsheetRenderer} writes the cell, so callers never pass raw
 * POI types or formula strings directly.
 *
 * <p>Use the static factories instead of the canonical constructor: they
 * enforce the same input contract the renderer would otherwise have to
 * re-check (numeric syntax, ISO date, formula whitelist prefix).</p>
 *
 * <p>{@link Kind#FORMULA} accepts only two whitelisted function names:
 * {@code SUM} and {@code AVERAGE}. Anything else is rejected at factory
 * time so the model cannot leak arbitrary Excel expressions into a
 * workbook.</p>
 */
public record SpreadsheetCellValue(Kind kind, String rawValue) {

    private static final Pattern FORMULA_WHITELIST =
            Pattern.compile("^(?i)(?:SUM|AVERAGE)\\([A-Z]{1,3}\\d+:[A-Z]{1,3}\\d+\\)$");

    public SpreadsheetCellValue {
        Objects.requireNonNull(kind, "kind 不能为空");
        Objects.requireNonNull(rawValue, "rawValue 不能为空");
        if (rawValue.isEmpty()) {
            throw new IllegalArgumentException("rawValue 不能为空");
        }
        validateForKind(kind, rawValue);
    }

    /** Generic string cell. */
    public static SpreadsheetCellValue of(String value) {
        return new SpreadsheetCellValue(Kind.STRING, value);
    }

    /** Numeric cell. Accepts integer or decimal literals; {@code null}/blank rejected. */
    public static SpreadsheetCellValue ofNumber(Number value) {
        Objects.requireNonNull(value, "value 不能为空");
        return new SpreadsheetCellValue(Kind.NUMBER, value.toString());
    }

    /** Numeric cell from a string literal; rejects malformed input at factory time. */
    public static SpreadsheetCellValue ofNumber(String literal) {
        Objects.requireNonNull(literal, "literal 不能为空");
        try {
            Double.parseDouble(literal);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "NUMBER 类型的 rawValue 必须是合法数字：" + literal, exception);
        }
        return new SpreadsheetCellValue(Kind.NUMBER, literal);
    }

    /** ISO-8601 {@code yyyy-MM-dd} date cell. */
    public static SpreadsheetCellValue ofDate(LocalDate date) {
        Objects.requireNonNull(date, "date 不能为空");
        return new SpreadsheetCellValue(Kind.DATE, date.toString());
    }

    /** ISO-8601 date cell from a string literal. */
    public static SpreadsheetCellValue ofDate(String literal) {
        Objects.requireNonNull(literal, "literal 不能为空");
        try {
            LocalDate.parse(literal);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "DATE 类型的 rawValue 必须是 ISO yyyy-MM-dd 日期：" + literal, exception);
        }
        return new SpreadsheetCellValue(Kind.DATE, literal);
    }

    /**
     * Formula cell. Only {@code SUM} and {@code AVERAGE} over a single
     * rectangular range ({@code A1:B2}) are accepted. Other shapes are
     * rejected so the model cannot smuggle arbitrary Excel expressions.
     */
    public static SpreadsheetCellValue ofFormula(String formula) {
        Objects.requireNonNull(formula, "formula 不能为空");
        String normalized = formula.trim();
        if (normalized.startsWith("=")) {
            normalized = normalized.substring(1);
        }
        if (!FORMULA_WHITELIST.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "FORMULA 仅支持 SUM/AVERAGE 单一矩形区域，收到：" + formula);
        }
        return new SpreadsheetCellValue(Kind.FORMULA, normalized.toUpperCase(Locale.ROOT));
    }

    private static void validateForKind(Kind kind, String rawValue) {
        switch (kind) {
            case STRING -> {
                if (rawValue.length() > 32_767) {
                    throw new IllegalArgumentException("STRING 长度超过 32767");
                }
            }
            case NUMBER -> {
                try {
                    Double.parseDouble(rawValue);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(
                            "NUMBER 类型的 rawValue 必须是合法数字：" + rawValue, exception);
                }
            }
            case DATE -> {
                try {
                    LocalDate.parse(rawValue);
                } catch (DateTimeParseException exception) {
                    throw new IllegalArgumentException(
                            "DATE 类型的 rawValue 必须是 ISO yyyy-MM-dd 日期：" + rawValue,
                            exception);
                }
            }
            case FORMULA -> {
                if (!FORMULA_WHITELIST.matcher(rawValue).matches()) {
                    throw new IllegalArgumentException(
                            "FORMULA 仅支持 SUM/AVERAGE 单一矩形区域，收到：" + rawValue);
                }
            }
        }
    }

    /**
     * Cell rendering kind.
     */
    public enum Kind {
        STRING,
        NUMBER,
        DATE,
        FORMULA
    }
}