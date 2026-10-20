package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.DocumentGenerationCapability;
import com.weida.bot.wechatilinkbot.infrastructure.document.DocumentGenerationProperties;
import com.weida.bot.wechatilinkbot.infrastructure.document.DocumentRenderer;
import com.weida.bot.wechatilinkbot.infrastructure.document.PdfDocumentClient;
import com.weida.bot.wechatilinkbot.infrastructure.document.WordDocumentClient;
import com.weida.bot.wechatilinkbot.tool.input.DocumentGenerationToolInput;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end smoke test that exercises the tool entry point with both the
 * structured and the plain-text paths.
 */
class DocumentGenerationToolTest {

    @TempDir
    Path outputDirectory;

    @Test
    void structuredSectionsJsonProducesChannelMedia() {
        DocumentGenerationTool tool = tool();

        String sectionsJson = "["
                + "{\"type\":\"heading_1\",\"text\":\"章节一\"},"
                + "{\"type\":\"paragraph\",\"text\":\"这是正文段落。\"},"
                + "{\"type\":\"bullet_list\",\"items\":[\"要点一\",\"要点二\"]},"
                + "{\"type\":\"table\",\"rows\":[[\"列 A\",\"列 B\"],[\"a1\",\"b1\"]]}"
                + "]";

        ToolExecution execution = tool.execute(
                new DocumentGenerationToolInput(
                        "report.docx",
                        "结构化报告",
                        null,
                        sectionsJson),
                new ToolContext("conv-1", "user-1"));

        assertThat(execution.generatedMedia()).isNotNull();
        assertThat(execution.generatedMedia().fileName()).endsWith(".docx");
        assertThat(execution.generatedMedia().bytes()).isNotEmpty();
        assertThat(execution.content()).contains("\"status\":\"ok\"");
    }

    @Test
    void rejectsSectionsJsonAndContentBothProvided() {
        DocumentGenerationTool tool = tool();

        ToolExecution execution = tool.execute(
                new DocumentGenerationToolInput(
                        "report.docx",
                        "title",
                        "plain text content",
                        "[{\"type\":\"paragraph\",\"text\":\"x\"}]"),
                new ToolContext("conv-1", "user-1"));

        assertThat(execution.generatedMedia()).isNull();
        assertThat(execution.content()).contains("\"status\":\"error\"");
        assertThat(execution.content())
                .contains("不能同时提供")
                .contains("\"retryable\":true");
    }

    @Test
    void rejectsInvalidJson() {
        DocumentGenerationTool tool = tool();

        ToolExecution execution = tool.execute(
                new DocumentGenerationToolInput(
                        "report.docx",
                        null,
                        null,
                        "{\"not\":\"an array\"}"),
                new ToolContext("conv-1", "user-1"));

        assertThat(execution.generatedMedia()).isNull();
        assertThat(execution.content())
                .contains("JSON 数组")
                .contains("\"retryable\":true");
    }

    @Test
    void rejectsUnknownSectionType() {
        DocumentGenerationTool tool = tool();

        ToolExecution execution = tool.execute(
                new DocumentGenerationToolInput(
                        "report.docx",
                        null,
                        null,
                        "[{\"type\":\"marquee\",\"text\":\"x\"}]"),
                new ToolContext("conv-1", "user-1"));

        assertThat(execution.generatedMedia()).isNull();
        assertThat(execution.content()).contains("不支持");
    }

    @Test
    void plainTextPathStillWorks() {
        DocumentGenerationTool tool = tool();

        ToolExecution execution = tool.execute(
                new DocumentGenerationToolInput(
                        "report.docx",
                        "legacy title",
                        "line one\\nline two",
                        null),
                new ToolContext("conv-1", "user-1"));

        assertThat(execution.generatedMedia()).isNotNull();
        assertThat(execution.generatedMedia().fileName()).isEqualTo("report.docx");
    }

    private DocumentGenerationTool tool() {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(outputDirectory);
        DocumentRenderer renderer = new DocumentRenderer(properties);
        WordDocumentClient wordClient = new WordDocumentClient(renderer, properties);
        DocumentGenerationCapability capability = new DocumentGenerationCapability(
                wordClient,
                new PdfDocumentClient(properties),
                properties);
        return new DocumentGenerationTool(capability, new ObjectMapper());
    }
}
