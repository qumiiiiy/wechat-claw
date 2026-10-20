package com.weida.bot.wechatilinkbot.model;

import java.util.Objects;

/**
 * Sealed hierarchy of DOCX edit operations accepted by
 * {@code DocumentRenderer.edit}. Each variant declares a single logical
 * change; the renderer applies them in list order, mutating the
 * in-memory document before serialization.
 *
 * <p>All matching-text variants compare against the full paragraph text
 * (i.e. the concatenation of all run texts) using exact (case-sensitive)
 * equality. The first matching paragraph is chosen; ambiguity is
 * reported as an {@link IllegalArgumentException} by the renderer so
 * callers can disambiguate intentionally.</p>
 *
 * <p>Table cell edits address cells by {@code (tableIndex, rowIndex,
 * colIndex)} using the document's natural table order; out-of-range
 * indices fail fast.</p>
 */
public sealed interface DocumentEdit
        permits DocumentEdit.AppendParagraph,
        DocumentEdit.DeleteParagraph,
        DocumentEdit.ChangeHeadingLevel,
        DocumentEdit.SetTableCell {

    /** Inline paragraph style. Mirrors the {@code DocumentSection} family. */
    enum ParagraphStyle {
        BODY,
        HEADING_1,
        HEADING_2
    }

    /**
     * Insert a new paragraph immediately after the first paragraph whose
     * text equals {@code afterText}. The new paragraph is rendered with
     * the requested style.
     */
    record AppendParagraph(
            String afterText,
            String newText,
            ParagraphStyle style) implements DocumentEdit {

        public AppendParagraph {
            Objects.requireNonNull(afterText, "afterText 不能为空");
            if (afterText.isEmpty()) {
                throw new IllegalArgumentException("afterText 不能为空");
            }
            Objects.requireNonNull(newText, "newText 不能为空");
            if (newText.isEmpty()) {
                throw new IllegalArgumentException("newText 不能为空");
            }
            Objects.requireNonNull(style, "style 不能为空");
        }
    }

    /**
     * Delete the first paragraph whose text equals {@code matchingText}.
     * Deleting the document title (row 0) is rejected by the renderer.
     */
    record DeleteParagraph(String matchingText) implements DocumentEdit {
        public DeleteParagraph {
            Objects.requireNonNull(matchingText, "matchingText 不能为空");
            if (matchingText.isEmpty()) {
                throw new IllegalArgumentException("matchingText 不能为空");
            }
        }
    }

    /**
     * Change the inline style of the first paragraph whose text equals
     * {@code matchingText}. Use to upgrade {@code BODY} content into a
     * heading or downgrade a heading back into body text.
     */
    record ChangeHeadingLevel(
            String matchingText,
            ParagraphStyle newLevel) implements DocumentEdit {

        public ChangeHeadingLevel {
            Objects.requireNonNull(matchingText, "matchingText 不能为空");
            if (matchingText.isEmpty()) {
                throw new IllegalArgumentException("matchingText 不能为空");
            }
            Objects.requireNonNull(newLevel, "newLevel 不能为空");
        }
    }

    /**
     * Replace the text of one table cell. The cell is addressed by
     * (tableIndex, rowIndex, colIndex) using the document's natural
     * order. Existing cell text is overwritten; formatting is preserved.
     */
    record SetTableCell(
            int tableIndex,
            int rowIndex,
            int colIndex,
            String newText) implements DocumentEdit {

        public SetTableCell {
            if (tableIndex < 0) {
                throw new IllegalArgumentException(
                        "tableIndex 不能为负数：" + tableIndex);
            }
            if (rowIndex < 0) {
                throw new IllegalArgumentException(
                        "rowIndex 不能为负数：" + rowIndex);
            }
            if (colIndex < 0) {
                throw new IllegalArgumentException(
                        "colIndex 不能为负数：" + colIndex);
            }
            Objects.requireNonNull(newText, "newText 不能为空");
        }
    }
}