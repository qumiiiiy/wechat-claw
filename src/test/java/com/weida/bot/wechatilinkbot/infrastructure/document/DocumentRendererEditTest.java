package com.weida.bot.wechatilinkbot.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.model.DocumentEditRequest;
import com.weida.bot.wechatilinkbot.model.DocumentEditRequest.TextReplacement;
import com.weida.bot.wechatilinkbot.model.DocumentRequest;
import com.weida.bot.wechatilinkbot.model.DocumentSection;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link DocumentRenderer#edit(byte[], DocumentEditRequest)}.
 * The renderer mutates workbook bytes only — no filesystem is touched here.
 */
class DocumentRendererEditTest {

    @Test
    void replacesTitleOnExistingDocument() throws Exception {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);

        DocumentRenderer.EditOutcome outcome = renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        "新标题",
                        List.of()));

        assertThat(outcome.replacementsApplied()).isZero();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(outcome.bytes()))) {
            XWPFParagraph first = document.getParagraphs().get(0);
            assertThat(first.getText()).isEqualTo("新标题");
        }
    }

    @Test
    void appliesCaseSensitiveReplacement() throws Exception {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);

        DocumentRenderer.EditOutcome outcome = renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(new TextReplacement("Hello", "你好", true))));

        assertThat(outcome.replacementsApplied()).isPositive();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(outcome.bytes()))) {
            assertThat(document.getParagraphs().get(2).getText()).contains("你好 World");
        }
    }

    @Test
    void appliesCaseInsensitiveReplacement() throws Exception {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);

        DocumentRenderer.EditOutcome outcome = renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(new TextReplacement("HELLO", "你好", false))));

        assertThat(outcome.replacementsApplied()).isPositive();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(outcome.bytes()))) {
            assertThat(document.getParagraphs().get(2).getText()).contains("你好 World");
        }
    }

    @Test
    void replacementDoesNothingWhenFindMissing() throws Exception {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);

        DocumentRenderer.EditOutcome outcome = renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(new TextReplacement("not present", "x", true))));

        assertThat(outcome.replacementsApplied()).isZero();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(outcome.bytes()))) {
            assertThat(document.getParagraphs().get(2).getText()).contains("Hello World");
        }
    }

    @Test
    void rejectsNewTitleStartingWithEqualsSign() {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);
        assertThatThrownBy(() -> renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        "=evil",
                        List.of())))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void rejectsReplacementStartingWithEqualsSign() {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);
        assertThatThrownBy(() -> renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(new TextReplacement("Hello", "=SUM(1)", true)))))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void rejectsNewTitleStartingWithTab() {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);
        assertThatThrownBy(() -> renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        "\tsneaky",
                        List.of())))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void rejectsRequestWithNeitherTitleNorReplacements() {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);
        assertThatThrownBy(() -> renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newTitle");
    }

    @Test
    void appliesReplacementsToTableCells() throws Exception {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new DocumentRequest(
                "表格测试",
                List.of(
                        new DocumentSection.Heading1Section("章节"),
                        new DocumentSection.TableSection(List.of(
                                List.of("Apple", "1"),
                                List.of("Banana", "2"))))));

        DocumentRenderer.EditOutcome outcome = renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(new TextReplacement("Apple", "苹果", true))));

        assertThat(outcome.replacementsApplied()).isPositive();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(outcome.bytes()))) {
            XWPFParagraph cellText = document.getTables().get(0)
                    .getRow(0).getCell(0).getParagraphs().get(0);
            assertThat(cellText.getText()).contains("苹果");
        }
    }

    @Test
    void preservesNonReplacedParagraphs() throws Exception {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);

        DocumentRenderer.EditOutcome outcome = renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        "新标题",
                        List.of(new TextReplacement("Hello", "你好", true))));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(outcome.bytes()))) {
            // Title + heading + paragraph preserved (heading and paragraph
            // exist regardless of whether they were touched).
            assertThat(document.getParagraphs().size()).isGreaterThanOrEqualTo(3);
            assertThat(document.getParagraphs().get(1).getText()).startsWith("章节");
            assertThat(document.getParagraphs().get(2).getText()).contains("你好 World");
        }
    }

    @Test
    void rejectsWhenCapabilityDisabled() {
        DocumentRenderer renderer = disabledRenderer();
        byte[] source = renderSource(enabledRenderer());
        assertThatThrownBy(() -> renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        "x",
                        List.of())))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void appendParagraphInsertsAfterAnchor() throws Exception {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);

        DocumentRenderer.EditOutcome outcome = renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(),
                        List.of(new com.weida.bot.wechatilinkbot.model.DocumentEdit.AppendParagraph(
                                "章节一", "新追加段落",
                                com.weida.bot.wechatilinkbot.model.DocumentEdit.ParagraphStyle.BODY))));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(outcome.bytes()))) {
            // Title (row 0) + Heading (row 1) + new paragraph + original "Hello World" (row 3)
            assertThat(document.getParagraphs().get(2).getText()).isEqualTo("新追加段落");
            assertThat(document.getParagraphs().get(3).getText()).isEqualTo("Hello World");
        }
    }

    @Test
    void deleteParagraphRemovesMatchingRow() throws Exception {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);

        DocumentRenderer.EditOutcome outcome = renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(),
                        List.of(new com.weida.bot.wechatilinkbot.model.DocumentEdit.DeleteParagraph(
                                "Hello World"))));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(outcome.bytes()))) {
            // Title (row 0) + Heading (row 1); "Hello World" gone.
            assertThat(document.getParagraphs()).hasSize(2);
            assertThat(document.getParagraphs().get(1).getText()).isEqualTo("章节一");
        }
    }

    @Test
    void deleteParagraphRejectsDeletingTitle() {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);

        assertThatThrownBy(() -> renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(),
                        List.of(new com.weida.bot.wechatilinkbot.model.DocumentEdit.DeleteParagraph(
                                "原始标题")))))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("拒绝删除标题段落");
    }

    @Test
    void changeHeadingLevelRewritesStyle() throws Exception {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);

        DocumentRenderer.EditOutcome outcome = renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(),
                        List.of(new com.weida.bot.wechatilinkbot.model.DocumentEdit.ChangeHeadingLevel(
                                "Hello World",
                                com.weida.bot.wechatilinkbot.model.DocumentEdit.ParagraphStyle.HEADING_2))));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(outcome.bytes()))) {
            XWPFParagraph paragraph = document.getParagraphs().get(2);
            assertThat(paragraph.getText()).isEqualTo("Hello World");
            assertThat(paragraph.getStyle()).isEqualTo("Heading2");
        }
    }

    @Test
    void setTableCellOverwritesText() throws Exception {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new DocumentRequest(
                "表格测试",
                List.of(
                        new DocumentSection.Heading1Section("章节"),
                        new DocumentSection.TableSection(List.of(
                                List.of("Apple", "1"),
                                List.of("Banana", "2"))))));

        DocumentRenderer.EditOutcome outcome = renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(),
                        List.of(new com.weida.bot.wechatilinkbot.model.DocumentEdit.SetTableCell(
                                0, 1, 0, "Cherry"))));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(outcome.bytes()))) {
            assertThat(document.getTables().get(0).getRow(1).getCell(0).getText())
                    .isEqualTo("Cherry");
            assertThat(document.getTables().get(0).getRow(1).getCell(1).getText())
                    .isEqualTo("2");
        }
    }

    @Test
    void setTableCellRejectsOutOfRangeIndex() {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderer.render(new DocumentRequest(
                "表格",
                List.of(new DocumentSection.TableSection(List.of(List.of("A", "B"))))));

        assertThatThrownBy(() -> renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(),
                        List.of(new com.weida.bot.wechatilinkbot.model.DocumentEdit.SetTableCell(
                                99, 0, 0, "x")))))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("tableIndex");
    }

    @Test
    void appendParagraphRejectsFormulaInjectionInNewText() {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);
        assertThatThrownBy(() -> renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        null,
                        List.of(),
                        List.of(new com.weida.bot.wechatilinkbot.model.DocumentEdit.AppendParagraph(
                                "章节一", "=SUM(A1:A2)",
                                com.weida.bot.wechatilinkbot.model.DocumentEdit.ParagraphStyle.BODY)))))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void editsCanBeCombinedWithReplacements() throws Exception {
        DocumentRenderer renderer = enabledRenderer();
        byte[] source = renderSource(renderer);

        DocumentRenderer.EditOutcome outcome = renderer.edit(source,
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        "新标题",
                        List.of(new com.weida.bot.wechatilinkbot.model.DocumentEditRequest.TextReplacement(
                                "Hello", "你好", true)),
                        List.of(new com.weida.bot.wechatilinkbot.model.DocumentEdit.DeleteParagraph(
                                "章节一"))));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(outcome.bytes()))) {
            assertThat(document.getParagraphs().get(0).getText()).isEqualTo("新标题");
            // Row 1: "你好 World" (the "Hello" replacement happened).
            assertThat(document.getParagraphs().get(1).getText()).isEqualTo("你好 World");
            // "章节一" deleted.
            assertThat(document.getParagraphs()).hasSize(2);
        }
    }

    @Test
    void rejectsEmptySourceBytes() {
        DocumentRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.edit(new byte[0],
                new DocumentEditRequest(
                        java.nio.file.Path.of("ignored.docx"),
                        "x",
                        List.of())))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("源字节为空");
    }

    private static byte[] renderSource(DocumentRenderer renderer) {
        return renderer.render(new DocumentRequest(
                "原始标题",
                List.of(
                        new DocumentSection.Heading1Section("章节一"),
                        new DocumentSection.ParagraphSection("Hello World"))));
    }

    private static DocumentRenderer enabledRenderer() {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(true);
        return new DocumentRenderer(properties);
    }

    private static DocumentRenderer disabledRenderer() {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(false);
        return new DocumentRenderer(properties);
    }
}