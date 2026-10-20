package com.weida.bot.wechatilinkbot.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.model.DocumentRequest;
import com.weida.bot.wechatilinkbot.model.DocumentSection;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

/**
 * Tests the pure POI renderer. No filesystem interaction; verification is
 * done by re-opening the produced byte buffer with POI.
 */
class DocumentRendererTest {

    @Test
    void rendersStructuredSectionsIntoReopenableDocument() throws IOException {
        DocumentRenderer renderer = renderer();

        DocumentRequest request = new DocumentRequest(
                "文档标题",
                List.of(
                        new DocumentSection.Heading1Section("一级标题示例"),
                        new DocumentSection.Heading2Section("二级标题示例"),
                        new DocumentSection.ParagraphSection("这是一段正文内容，用于验证段落渲染。"),
                        new DocumentSection.BulletListSection(List.of("要点一", "要点二")),
                        new DocumentSection.OrderedListSection(List.of("步骤一", "步骤二")),
                        new DocumentSection.TableSection(List.of(
                                List.of("列 A", "列 B"),
                                List.of("a1", "b1"),
                                List.of("a2", "b2"))),
                        new DocumentSection.PageBreakSection(),
                        new DocumentSection.ParagraphSection("分页后的内容")));

        byte[] bytes = renderer.render(request);

        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            assertThat(document.getParagraphs().size()).isGreaterThanOrEqualTo(8);
            assertThat(document.getTables()).hasSize(1);

            XWPFTable table = document.getTables().get(0);
            assertThat(table.getRows()).hasSize(3);
            assertThat(table.getRow(0).getTableCells()).hasSize(2);
            assertThat(table.getRow(0).getCell(0).getText()).isEqualTo("列 A");
            assertThat(table.getRow(2).getCell(1).getText()).isEqualTo("b2");

            int pageBreaks = 0;
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                for (org.apache.poi.xwpf.usermodel.XWPFRun run : paragraph.getRuns()) {
                    for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr br
                            : run.getCTR().getBrList()) {
                        if (br.getType()
                                == org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType.PAGE) {
                            pageBreaks++;
                        }
                    }
                }
            }
            assertThat(pageBreaks).isEqualTo(1);
        }
    }

    @Test
    void rendersLongChineseParagraphWithoutCrash() {
        DocumentRenderer renderer = renderer();
        String longText = "中文字符测试。".repeat(200);
        DocumentRequest request = new DocumentRequest(
                null,
                List.of(new DocumentSection.ParagraphSection(longText)));

        byte[] bytes = renderer.render(request);

        assertThat(bytes).isNotEmpty();
        assertThat(bytes.length).isLessThan(rendererBytesLimit());
    }

    @Test
    void rejectsRequestExceedingSectionLimit() {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setMaxSections(2);
        DocumentRenderer renderer = new DocumentRenderer(properties);

        DocumentRequest request = new DocumentRequest(
                "title",
                List.of(
                        new DocumentSection.ParagraphSection("a"),
                        new DocumentSection.ParagraphSection("b"),
                        new DocumentSection.ParagraphSection("c")));

        assertThatThrownBy(() -> renderer.render(request))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("section 总数");
    }

    @Test
    void rejectsTableExceedingRowLimit() {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setMaxTableRows(2);
        DocumentRenderer renderer = new DocumentRenderer(properties);

        DocumentRequest request = new DocumentRequest(
                "title",
                List.of(new DocumentSection.TableSection(List.of(
                        List.of("a", "b"),
                        List.of("c", "d"),
                        List.of("e", "f")))));

        assertThatThrownBy(() -> renderer.render(request))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("表格行数");
    }

    @Test
    void rejectsParagraphExceedingCharacterLimit() {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setMaxParagraphCharacters(5);
        DocumentRenderer renderer = new DocumentRenderer(properties);

        DocumentRequest request = new DocumentRequest(
                "title",
                List.of(new DocumentSection.ParagraphSection("abcdefghij")));

        assertThatThrownBy(() -> renderer.render(request))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("超过最大字符数");
    }

    @Test
    void rejectsTableWithInconsistentColumnCount() {
        DocumentRenderer renderer = renderer();

        assertThatThrownBy(() -> new DocumentSection.TableSection(List.of(
                List.of("a", "b"),
                List.of("c"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void rejectsRequestWhenDisabled() {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(false);
        DocumentRenderer renderer = new DocumentRenderer(properties);

        DocumentRequest request = new DocumentRequest(
                "title",
                List.of(new DocumentSection.ParagraphSection("a")));

        assertThatThrownBy(() -> renderer.render(request))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("未启用");
    }

    private DocumentRenderer renderer() {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(true);
        return new DocumentRenderer(properties);
    }

    private int rendererBytesLimit() {
        return 10 * 1024 * 1024;
    }
}