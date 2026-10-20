package com.weida.bot.wechatilinkbot.model;

/**
 * Bounded building block for one structured DOCX document.
 *
 * <p>Sections are immutable, validated at construction, and intentionally
 * simple. The renderer never inspects model state beyond what each section
 * exposes through this sealed interface.</p>
 *
 * <p>The model layer owns nothing about rendering, IO, or AI; this package
 * is shared infrastructure that other modules already depend on.</p>
 */
public sealed interface DocumentSection
        permits DocumentSection.Heading1Section,
                DocumentSection.Heading2Section,
                DocumentSection.ParagraphSection,
                DocumentSection.BulletListSection,
                DocumentSection.OrderedListSection,
                DocumentSection.TableSection,
                DocumentSection.PageBreakSection {

    /** Heading level 1 text. */
    record Heading1Section(String text) implements DocumentSection {

        public Heading1Section {
            if (text == null) {
                throw new IllegalArgumentException("一级标题文本不能为空");
            }
            text = normalize(text);
            if (text.isEmpty()) {
                throw new IllegalArgumentException("一级标题文本不能为空");
            }
        }
    }

    /** Heading level 2 text. */
    record Heading2Section(String text) implements DocumentSection {

        public Heading2Section {
            if (text == null) {
                throw new IllegalArgumentException("二级标题文本不能为空");
            }
            text = normalize(text);
            if (text.isEmpty()) {
                throw new IllegalArgumentException("二级标题文本不能为空");
            }
        }
    }

    /**
     * Plain text paragraph. Empty paragraphs are preserved as a single visual
     * blank line and never collapsed.
     */
    record ParagraphSection(String text) implements DocumentSection {

        public ParagraphSection {
            if (text == null) {
                throw new IllegalArgumentException("段落文本不能为空");
            }
            text = normalize(text);
        }
    }

    /** Unordered list. Each item is a separate visual line. */
    record BulletListSection(java.util.List<String> items) implements DocumentSection {

        public BulletListSection {
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("项目列表项不能为空");
            }
            java.util.List<String> copy = new java.util.ArrayList<>(items.size());
            for (String item : items) {
                if (item == null) {
                    throw new IllegalArgumentException("项目列表项不能为 null");
                }
                copy.add(normalize(item));
            }
            items = java.util.Collections.unmodifiableList(copy);
        }
    }

    /** Ordered (numbered) list. Each item is a separate visual line. */
    record OrderedListSection(java.util.List<String> items) implements DocumentSection {

        public OrderedListSection {
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("有序列表项不能为空");
            }
            java.util.List<String> copy = new java.util.ArrayList<>(items.size());
            for (String item : items) {
                if (item == null) {
                    throw new IllegalArgumentException("有序列表项不能为 null");
                }
                copy.add(normalize(item));
            }
            items = java.util.Collections.unmodifiableList(copy);
        }
    }

    /**
     * Simple bordered table. The first row is treated as the header and is
     * repeated on every page break. Cell values are plain text.
     */
    record TableSection(java.util.List<java.util.List<String>> rows) implements DocumentSection {

        public TableSection {
            if (rows == null || rows.isEmpty()) {
                throw new IllegalArgumentException("表格行不能为空");
            }
            java.util.List<java.util.List<String>> copy = new java.util.ArrayList<>(rows.size());
            int width = -1;
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                java.util.List<String> row = rows.get(rowIndex);
                if (row == null || row.isEmpty()) {
                    throw new IllegalArgumentException("表格第 " + (rowIndex + 1) + " 行为空");
                }
                if (width < 0) {
                    width = row.size();
                } else if (row.size() != width) {
                    throw new IllegalArgumentException(
                            "表格第 " + (rowIndex + 1) + " 行单元格数与表头不一致");
                }
                java.util.List<String> rowCopy = new java.util.ArrayList<>(row.size());
                for (String cell : row) {
                    if (cell == null) {
                        throw new IllegalArgumentException("表格单元格不能为 null");
                    }
                    rowCopy.add(normalize(cell));
                }
                copy.add(java.util.Collections.unmodifiableList(rowCopy));
            }
            rows = java.util.Collections.unmodifiableList(copy);
        }
    }

    /** Explicit page break. */
    record PageBreakSection() implements DocumentSection {
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC);
    }
}