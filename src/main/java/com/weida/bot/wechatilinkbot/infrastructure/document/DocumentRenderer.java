package com.weida.bot.wechatilinkbot.infrastructure.document;

import com.weida.bot.wechatilinkbot.model.DocumentEdit;
import com.weida.bot.wechatilinkbot.model.DocumentEdit.AppendParagraph;
import com.weida.bot.wechatilinkbot.model.DocumentEdit.ChangeHeadingLevel;
import com.weida.bot.wechatilinkbot.model.DocumentEdit.DeleteParagraph;
import com.weida.bot.wechatilinkbot.model.DocumentEdit.SetTableCell;
import com.weida.bot.wechatilinkbot.model.DocumentEditRequest;
import com.weida.bot.wechatilinkbot.model.DocumentRequest;
import com.weida.bot.wechatilinkbot.model.DocumentSection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.math.BigInteger;
import java.util.List;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pure POI DOCX renderer.
 *
 * <p>Stateless and side-effect free: accepts a validated
 * {@link DocumentRequest} and returns the resulting document bytes. The
 * capability layer owns filesystem I/O and POI re-read verification.</p>
 *
 * <p>Layout produced by {@link #render}:</p>
 * <ul>
 *   <li>Optional centered title rendered in 22pt bold; East-Asian glyphs use
 *       宋体, Western glyphs use Times New Roman.</li>
 *   <li>Heading 1 sections: 16pt bold, 24pt before / 6pt after.</li>
 *   <li>Heading 2 sections: 14pt bold, 18pt before / 6pt after.</li>
 *   <li>Paragraph sections: 12pt regular, line spacing 1.5x, 6pt after.</li>
 *   <li>Bullet lists: project symbol bullet, 12pt.</li>
 *   <li>Ordered lists: decimal numbering, 12pt.</li>
 *   <li>Tables: black 4pt single-line borders on every side, first row
 *       repeated as the header on every page break.</li>
 *   <li>Explicit page breaks: hard break inserted into the next paragraph.</li>
 *   <li>A4 page size with 1440 twips (1 inch) margins on all four sides.</li>
 * </ul>
 */
@Component
public class DocumentRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentRenderer.class);

    private static final String EAST_ASIAN_FONT = "宋体";
    private static final String WESTERN_FONT = "Times New Roman";
    private static final int TITLE_FONT_SIZE = 22;
    private static final int H1_FONT_SIZE = 16;
    private static final int H2_FONT_SIZE = 14;
    private static final int BODY_FONT_SIZE = 12;
    private static final int TABLE_FONT_SIZE = 11;
    private static final int MARGIN_TWIPS = 1440;
    private static final BigInteger A4_WIDTH = BigInteger.valueOf(11906);   // 210mm
    private static final BigInteger A4_HEIGHT = BigInteger.valueOf(16838);  // 297mm

    private final DocumentGenerationProperties properties;

    public DocumentRenderer(DocumentGenerationProperties properties) {
        this.properties = properties;
    }

    public boolean isAvailable() {
        return properties.isEnabled();
    }

    public byte[] render(DocumentRequest request) {
        if (!isAvailable()) {
            throw new DocumentGenerationException("文档生成未启用");
        }
        validateRequest(request);

        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            applyPageSetup(document);

            if (request.title() != null) {
                renderTitle(document, request.title());
            }

            for (DocumentSection section : request.sections()) {
                if (section instanceof DocumentSection.Heading1Section h1) {
                    renderHeading1(document, h1.text());
                } else if (section instanceof DocumentSection.Heading2Section h2) {
                    renderHeading2(document, h2.text());
                } else if (section instanceof DocumentSection.ParagraphSection p) {
                    renderParagraph(document, p.text());
                } else if (section instanceof DocumentSection.BulletListSection b) {
                    renderBulletList(document, b.items());
                } else if (section instanceof DocumentSection.OrderedListSection o) {
                    renderOrderedList(document, o.items());
                } else if (section instanceof DocumentSection.TableSection t) {
                    renderTable(document, t.rows());
                } else if (section instanceof DocumentSection.PageBreakSection) {
                    renderPageBreak(document);
                } else {
                    throw new DocumentGenerationException(
                            "不支持的 section 类型：" + section.getClass().getName());
                }
            }

            document.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new DocumentGenerationException(
                        "生成的 DOCX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            return bytes;
        } catch (IOException exception) {
            throw new DocumentGenerationException("DOCX 渲染失败", exception);
        }
    }

    private void validateRequest(DocumentRequest request) {
        int sectionCount = (request.title() != null ? 1 : 0) + request.sections().size();
        if (sectionCount > properties.getMaxSections()) {
            throw new DocumentGenerationException(
                    "section 总数超过最大限制：" + properties.getMaxSections());
        }
        if (request.title() != null
                && request.title().length() > properties.getMaxParagraphCharacters()) {
            throw new DocumentGenerationException(
                    "title 超过最大字符数：" + properties.getMaxParagraphCharacters());
        }

        for (DocumentSection section : request.sections()) {
            validateSection(section);
        }
    }

    private void validateSection(DocumentSection section) {
        if (section instanceof DocumentSection.Heading1Section h1) {
            ensureUnderLimit("一级标题", h1.text().length(),
                    properties.getMaxParagraphCharacters());
        } else if (section instanceof DocumentSection.Heading2Section h2) {
            ensureUnderLimit("二级标题", h2.text().length(),
                    properties.getMaxParagraphCharacters());
        } else if (section instanceof DocumentSection.ParagraphSection p) {
            ensureUnderLimit("段落", p.text().length(),
                    properties.getMaxParagraphCharacters());
        } else if (section instanceof DocumentSection.BulletListSection b) {
            validateList("项目列表", b.items(), properties.getMaxListItems());
            for (String item : b.items()) {
                ensureUnderLimit("项目列表项", item.length(),
                        properties.getMaxParagraphCharacters());
            }
        } else if (section instanceof DocumentSection.OrderedListSection o) {
            validateList("有序列表", o.items(), properties.getMaxListItems());
            for (String item : o.items()) {
                ensureUnderLimit("有序列表项", item.length(),
                        properties.getMaxParagraphCharacters());
            }
        } else if (section instanceof DocumentSection.TableSection t) {
            validateTable(t.rows());
        } else if (section instanceof DocumentSection.PageBreakSection) {
            // no payload
        }
    }

    private void validateList(String label, List<String> items, int maxItems) {
        if (items.size() > maxItems) {
            throw new DocumentGenerationException(
                    label + " 项数超过最大限制：" + maxItems);
        }
    }

    private void validateTable(List<List<String>> rows) {
        if (rows.size() > properties.getMaxTableRows()) {
            throw new DocumentGenerationException(
                    "表格行数超过最大限制：" + properties.getMaxTableRows());
        }
        int width = rows.get(0).size();
        if (width > properties.getMaxTableColumns()) {
            throw new DocumentGenerationException(
                    "表格列数超过最大限制：" + properties.getMaxTableColumns());
        }
        for (List<String> row : rows) {
            for (String cell : row) {
                ensureUnderLimit("表格单元格", cell.length(),
                        properties.getMaxTableCellCharacters());
            }
        }
    }

    private void ensureUnderLimit(String label, int length, int limit) {
        if (length > limit) {
            throw new DocumentGenerationException(
                    label + " 超过最大字符数：" + limit);
        }
    }

    private void applyPageSetup(XWPFDocument document) {
        CTBody body = document.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();

        CTPageSz pageSize = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSize.setW(A4_WIDTH);
        pageSize.setH(A4_HEIGHT);

        CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pageMar.setTop(MARGIN_TWIPS);
        pageMar.setRight(MARGIN_TWIPS);
        pageMar.setBottom(MARGIN_TWIPS);
        pageMar.setLeft(MARGIN_TWIPS);
        pageMar.setHeader(BigInteger.valueOf(720));
        pageMar.setFooter(BigInteger.valueOf(720));
        pageMar.setGutter(BigInteger.ZERO);
    }

    private void renderTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(240);
        XWPFRun run = paragraph.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(TITLE_FONT_SIZE);
        applyFont(run);
    }

    private void renderHeading1(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("Heading1");
        paragraph.setSpacingBefore(480);
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(H1_FONT_SIZE);
        applyFont(run);
    }

    private void renderHeading2(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("Heading2");
        paragraph.setSpacingBefore(360);
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(H2_FONT_SIZE);
        applyFont(run);
    }

    private void renderParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(120);
        paragraph.setSpacingLineRule(LineSpacingRule.AUTO);
        paragraph.setSpacingBetween(1.5, LineSpacingRule.AUTO);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(BODY_FONT_SIZE);
        applyFont(run);
    }

    private void renderBulletList(XWPFDocument document, List<String> items) {
        for (String item : items) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setNumID(BigInteger.ONE);
            paragraph.setIndentationLeft(420);
            paragraph.setIndentationHanging(210);
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(60);
            XWPFRun run = paragraph.createRun();
            run.setText(item);
            run.setFontSize(BODY_FONT_SIZE);
            applyFont(run);
        }
    }

    private void renderOrderedList(XWPFDocument document, List<String> items) {
        for (String item : items) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setNumID(BigInteger.valueOf(2));
            paragraph.setIndentationLeft(420);
            paragraph.setIndentationHanging(210);
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(60);
            XWPFRun run = paragraph.createRun();
            run.setText(item);
            run.setFontSize(BODY_FONT_SIZE);
            applyFont(run);
        }
    }

    private void renderTable(XWPFDocument document, List<List<String>> rows) {
        int width = rows.get(0).size();
        XWPFTable table = document.createTable(rows.size(), width);
        applyTableBorders(table);

        XWPFTableRow headerRow = table.getRow(0);
        headerRow.setRepeatHeader(true);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            List<String> values = rows.get(rowIndex);
            for (int colIndex = 0; colIndex < width; colIndex++) {
                XWPFTableCell cell = row.getCell(colIndex);
                XWPFParagraph paragraph = cell.getParagraphs().isEmpty()
                        ? cell.addParagraph()
                        : cell.getParagraphs().get(0);
                paragraph.setSpacingBefore(0);
                paragraph.setSpacingAfter(0);
                XWPFRun run = paragraph.createRun();
                run.setText(values.get(colIndex));
                run.setFontSize(TABLE_FONT_SIZE);
                if (rowIndex == 0) {
                    run.setBold(true);
                }
                applyFont(run);
            }
        }
    }

    private void renderPageBreak(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.addBreak(BreakType.PAGE);
    }

    private void applyTableBorders(XWPFTable table) {
        table.setTopBorder(XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setBottomBorder(XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setLeftBorder(XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setRightBorder(XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setInsideHBorder(XWPFBorderType.SINGLE, 4, 0, "000000");
        table.setInsideVBorder(XWPFBorderType.SINGLE, 4, 0, "000000");
    }

    private void applyFont(XWPFRun run) {
        run.setFontFamily(WESTERN_FONT, XWPFRun.FontCharRange.ascii);
        run.setFontFamily(WESTERN_FONT, XWPFRun.FontCharRange.hAnsi);
        run.setFontFamily(WESTERN_FONT, XWPFRun.FontCharRange.cs);
        run.setFontFamily(EAST_ASIAN_FONT, XWPFRun.FontCharRange.eastAsia);
    }

    /**
     * Loads an existing DOCX from in-memory bytes, applies the requested
     * edits (title rewrite + literal text replacements), and returns the
     * modified bytes. The source bytes are never mutated; the caller owns
     * the boundary check and atomic-write flow.
     *
     * <p>Allowed edits are intentionally narrow: a single title rewrite
     * and a list of literal find/replace pairs applied to every run in the
     * document body, including table cell paragraphs. No paragraph is
     * created or removed, no style is altered.</p>
     *
     * @return the modified workbook bytes; the caller decides where to land
     *         them on disk
     */
    public EditOutcome edit(byte[] sourceBytes, DocumentEditRequest request) {
        if (!isAvailable()) {
            throw new DocumentGenerationException("文档生成未启用");
        }
        Objects.requireNonNull(sourceBytes, "sourceBytes 不能为空");
        Objects.requireNonNull(request, "request 不能为空");
        if (sourceBytes.length == 0) {
            throw new DocumentGenerationException("源字节为空");
        }
        if (sourceBytes.length > properties.getMaxOutputBytes()) {
            throw new DocumentGenerationException(
                    "源 DOCX 超过最大字节数：" + properties.getMaxOutputBytes());
        }
        validateEditRequest(request);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(sourceBytes));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            applyPageSetup(document);

            if (request.newTitle() != null) {
                replaceTitle(document, request.newTitle());
            }
            int replacementsApplied = applyReplacements(document, request.replacements());
            applyEdits(document, request.edits());

            document.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length > properties.getMaxOutputBytes()) {
                throw new DocumentGenerationException(
                        "修改后 DOCX 超过最大字节数：" + properties.getMaxOutputBytes());
            }
            LOG.info(
                    "document_edited title_replaced={} replacements_applied={} bytes={}",
                    request.newTitle() != null,
                    replacementsApplied,
                    bytes.length);
            return new EditOutcome(bytes, replacementsApplied);
        } catch (IOException exception) {
            throw new DocumentGenerationException(
                    "DOCX 修改失败：" + exception.getMessage(), exception);
        }
    }

    private void validateEditRequest(DocumentEditRequest request) {
        if (request.newTitle() != null) {
            if (request.newTitle().length() > properties.getMaxParagraphCharacters()) {
                throw new DocumentGenerationException(
                        "newTitle 超过最大字符数：" + properties.getMaxParagraphCharacters());
            }
            rejectFormulaInjection(request.newTitle(), "newTitle");
        }
        for (int i = 0; i < request.replacements().size(); i++) {
            DocumentEditRequest.TextReplacement replacement = request.replacements().get(i);
            if (replacement.find().length() > properties.getMaxParagraphCharacters()) {
                throw new DocumentGenerationException(
                        "替换关键字 [" + i + "] 超过最大字符数");
            }
            rejectFormulaInjection(replacement.replacement(), "替换 [" + i + "]");
        }
        for (int i = 0; i < request.edits().size(); i++) {
            DocumentEdit edit = request.edits().get(i);
            if (edit instanceof AppendParagraph ap) {
                if (ap.newText().length() > properties.getMaxParagraphCharacters()) {
                    throw new DocumentGenerationException(
                            "edits[" + i + "] newText 超过最大字符数");
                }
                rejectFormulaInjection(ap.newText(), "edits[" + i + "] newText");
            } else if (edit instanceof ChangeHeadingLevel chl) {
                rejectFormulaInjection(chl.matchingText(), "edits[" + i + "] matchingText");
            } else if (edit instanceof DeleteParagraph dp) {
                rejectFormulaInjection(dp.matchingText(), "edits[" + i + "] matchingText");
            } else if (edit instanceof SetTableCell stc) {
                rejectFormulaInjection(stc.newText(), "edits[" + i + "] newText");
            }
        }
    }

    private void applyEdits(XWPFDocument document, List<DocumentEdit> edits) {
        int index = 0;
        for (DocumentEdit edit : edits) {
            index++;
            if (edit instanceof AppendParagraph ap) {
                appendParagraph(document, ap, index);
            } else if (edit instanceof DeleteParagraph dp) {
                deleteParagraph(document, dp, index);
            } else if (edit instanceof ChangeHeadingLevel chl) {
                changeHeadingLevel(document, chl, index);
            } else if (edit instanceof SetTableCell stc) {
                setTableCell(document, stc, index);
            }
        }
    }

    private void appendParagraph(XWPFDocument document, AppendParagraph edit, int editIndex) {
        XWPFParagraph anchor = findParagraphByText(document, edit.afterText(), editIndex);
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody body =
                document.getDocument().getBody();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP anchorCt =
                anchor.getCTP();
        int anchorIndex = -1;
        for (int i = 0; i < body.sizeOfPArray(); i++) {
            if (body.getPArray(i) == anchorCt) {
                anchorIndex = i;
                break;
            }
        }
        if (anchorIndex < 0) {
            // Anchor inside a table cell is not addressable as a body-level
            // insertion target. Reject the request explicitly so callers
            // can disambiguate by anchoring on a body-level paragraph.
            throw new DocumentGenerationException(
                    "edits[" + editIndex + "] anchor 段落不在 body 顶层："
                            + edit.afterText());
        }
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP newCt =
                body.insertNewP(anchorIndex + 1);
        XWPFParagraph newParagraph = new XWPFParagraph(newCt, document);
        applyStyle(newParagraph, edit.style());
        XWPFRun run = newParagraph.createRun();
        run.setText(edit.newText());
        run.setBold(true);
        run.setFontSize(edit.style() == DocumentEdit.ParagraphStyle.BODY ? 22 : TITLE_FONT_SIZE);
        applyFont(run);
    }

    private void deleteParagraph(XWPFDocument document, DeleteParagraph edit, int editIndex) {
        XWPFParagraph target = findParagraphByText(document, edit.matchingText(), editIndex);
        if (target == document.getParagraphs().get(0)) {
            throw new DocumentGenerationException(
                    "edits[" + editIndex + "] 拒绝删除标题段落");
        }
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody body =
                document.getDocument().getBody();
        for (int i = 0; i < body.sizeOfPArray(); i++) {
            if (body.getPArray(i) == target.getCTP()) {
                body.removeP(i);
                return;
            }
        }
        throw new DocumentGenerationException(
                "edits[" + editIndex + "] 段落不在 body 顶层："
                        + edit.matchingText());
    }

    private void changeHeadingLevel(XWPFDocument document, ChangeHeadingLevel edit,
            int editIndex) {
        XWPFParagraph target = findParagraphByText(document, edit.matchingText(), editIndex);
        // Preserve text before clearing runs, otherwise getText() returns
        // empty and the rewritten paragraph loses its content.
        String preservedText = target.getText() == null ? "" : target.getText();
        while (!target.getRuns().isEmpty()) {
            target.removeRun(0);
        }
        applyStyle(target, edit.newLevel());
        XWPFRun run = target.createRun();
        run.setText(preservedText);
        run.setBold(true);
        run.setFontSize(edit.newLevel() == DocumentEdit.ParagraphStyle.BODY ? 22 : TITLE_FONT_SIZE);
        applyFont(run);
    }

    private void setTableCell(XWPFDocument document, SetTableCell edit, int editIndex) {
        List<XWPFTable> tables = document.getTables();
        if (edit.tableIndex() >= tables.size()) {
            throw new DocumentGenerationException(
                    "edits[" + editIndex + "] tableIndex 超出范围："
                            + edit.tableIndex() + " >= " + tables.size());
        }
        XWPFTable table = tables.get(edit.tableIndex());
        if (edit.rowIndex() >= table.getRows().size()) {
            throw new DocumentGenerationException(
                    "edits[" + editIndex + "] rowIndex 超出范围："
                            + edit.rowIndex() + " >= " + table.getRows().size());
        }
        XWPFTableRow row = table.getRow(edit.rowIndex());
        if (edit.colIndex() >= row.getTableCells().size()) {
            throw new DocumentGenerationException(
                    "edits[" + editIndex + "] colIndex 超出范围："
                            + edit.colIndex() + " >= " + row.getTableCells().size());
        }
        XWPFTableCell cell = row.getCell(edit.colIndex());
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            for (XWPFRun run : paragraph.getRuns()) {
                run.setText("", 0);
            }
        }
        XWPFParagraph first = cell.getParagraphs().get(0);
        if (first.getRuns().isEmpty()) {
            first.createRun().setText(edit.newText());
        } else {
            first.getRuns().get(0).setText(edit.newText());
        }
    }

    private static XWPFParagraph findParagraphByText(
            XWPFDocument document, String text, int editIndex) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String currentText = paragraph.getText();
            if (currentText == null) {
                continue;
            }
            // Match by exact equality of the visible text. The renderer
            // re-uses a single styled run per paragraph, so the paragraph
            // text equals the run text in normal cases.
            if (currentText.equals(text)) {
                return paragraph;
            }
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        String currentText = paragraph.getText();
                        if (currentText != null && currentText.equals(text)) {
                            return paragraph;
                        }
                    }
                }
            }
        }
        throw new DocumentGenerationException(
                "edits[" + editIndex + "] 找不到匹配段落：" + text);
    }

    private static void applyStyle(XWPFParagraph paragraph, DocumentEdit.ParagraphStyle style) {
        switch (style) {
            case HEADING_1 -> {
                paragraph.setStyle("Heading1");
                paragraph.setAlignment(ParagraphAlignment.LEFT);
            }
            case HEADING_2 -> {
                paragraph.setStyle("Heading2");
                paragraph.setAlignment(ParagraphAlignment.LEFT);
            }
            case BODY -> {
                paragraph.setStyle("Normal");
                paragraph.setAlignment(ParagraphAlignment.LEFT);
            }
        }
    }

    private static void moveParagraphAfter(XWPFParagraph moved, XWPFParagraph anchor) {
        // Reserved for future relocation work. The appendParagraph code
        // path uses CTBody.insertNewP directly instead of cursor-based
        // moves because XmlCursor.moveXml rejects END-token positions.
        // Keeping this helper to avoid churn in case a future edit kind
        // needs it.
        throw new UnsupportedOperationException("moveParagraphAfter is unused");
    }

    private void rejectFormulaInjection(String value, String where) {
        if (value.isEmpty()) {
            return;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '@' || first == '-'
                || first == '\t' || first == '\r') {
            throw new DocumentGenerationException(
                    where + " 以公式注入前缀开头（首字符 0x"
                            + Integer.toHexString(first) + "），拒绝写入");
        }
    }

    private void replaceTitle(XWPFDocument document, String newTitle) {
        if (document.getParagraphs().isEmpty()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun run = paragraph.createRun();
            run.setText(newTitle);
            run.setBold(true);
            run.setFontSize(TITLE_FONT_SIZE);
            applyFont(run);
            return;
        }
        XWPFParagraph first = document.getParagraphs().get(0);
        // Clear existing runs and rewrite the title as a single styled run
        // so the title bar stays visually identical to the original render.
        while (!first.getRuns().isEmpty()) {
            first.removeRun(0);
        }
        XWPFRun run = first.createRun();
        run.setText(newTitle);
        run.setBold(true);
        run.setFontSize(TITLE_FONT_SIZE);
        applyFont(run);
    }

    private int applyReplacements(
            XWPFDocument document,
            List<DocumentEditRequest.TextReplacement> replacements) {
        if (replacements.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            total += applyReplacementsToParagraph(paragraph, replacements);
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        total += applyReplacementsToParagraph(paragraph, replacements);
                    }
                }
            }
        }
        return total;
    }

    private int applyReplacementsToParagraph(
            XWPFParagraph paragraph,
            List<DocumentEditRequest.TextReplacement> replacements) {
        int count = 0;
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.getText(0);
            if (text == null || text.isEmpty()) {
                continue;
            }
            String updated = text;
            for (DocumentEditRequest.TextReplacement r : replacements) {
                String before = updated;
                updated = replaceFirstOccurrence(
                        updated, r.find(), r.replacement(), r.caseSensitive());
                if (!updated.equals(before)) {
                    count++;
                }
            }
            if (!updated.equals(text)) {
                run.setText(updated, 0);
            }
        }
        return count;
    }

    private static String replaceFirstOccurrence(
            String input, String find, String replacement, boolean caseSensitive) {
        int index = caseSensitive
                ? input.indexOf(find)
                : indexOfIgnoreCase(input, find);
        if (index < 0) {
            return input;
        }
        return input.substring(0, index)
                + replacement
                + input.substring(index + find.length());
    }

    private static int indexOfIgnoreCase(String input, String find) {
        int max = input.length() - find.length();
        for (int i = 0; i <= max; i++) {
            if (input.regionMatches(true, i, find, 0, find.length())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Outcome of {@link #edit(byte[], DocumentEditRequest)}.
     *
     * @param bytes                the modified workbook bytes
     * @param replacementsApplied  count of run-level text replacements that
     *                             actually changed a run (multiple matches
     *                             within a single run still count as one)
     */
    public record EditOutcome(byte[] bytes, int replacementsApplied) {
        public EditOutcome {
            Objects.requireNonNull(bytes, "bytes 不能为空");
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}