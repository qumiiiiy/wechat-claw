package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.infrastructure.document.DocumentGenerationProperties;
import com.weida.bot.wechatilinkbot.infrastructure.document.DocumentRenderer;
import com.weida.bot.wechatilinkbot.infrastructure.document.PdfDocumentClient;
import com.weida.bot.wechatilinkbot.infrastructure.document.WordDocumentClient;
import com.weida.bot.wechatilinkbot.model.DocumentRequest;
import com.weida.bot.wechatilinkbot.model.DocumentSection;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Capability-level tests for the structured DOCX entry point and the
 * backward-compatible plain-text path.
 */
class DocumentGenerationCapabilityTest {

    @TempDir
    java.nio.file.Path outputDirectory;

    @Test
    void structuredEntryProducesChannelReadyMedia() throws IOException {
        DocumentGenerationCapability capability = capability();

        DocumentRequest request = new DocumentRequest(
                "结构化验证",
                List.of(
                        new DocumentSection.Heading1Section("标题一"),
                        new DocumentSection.ParagraphSection("正文段落。"),
                        new DocumentSection.BulletListSection(List.of("a", "b"))));

        GeneratedMedia media = capability.generate("report.docx", request);

        assertThat(media.kind()).isEqualTo(GeneratedMediaKind.FILE);
        assertThat(media.fileName()).endsWith(".docx");
        assertThat(media.bytes()).isNotEmpty();
        assertThat(media.bytes().length).isGreaterThan(0);
    }

    @Test
    void structuredEntryRejectsNonDocxFileName() {
        DocumentGenerationCapability capability = capability();
        DocumentRequest request = new DocumentRequest(
                "title",
                List.of(new DocumentSection.ParagraphSection("x")));

        assertThatThrownBy(() -> capability.generate("report.pdf", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("结构化入口仅支持 .docx");
    }

    @Test
    void legacyEntryStillGeneratesDocx() throws IOException {
        DocumentGenerationCapability capability = capability();

        GeneratedMedia media = capability.generate(
                "legacy.docx", "legacy title", "line one\nline two");

        assertThat(media.kind()).isEqualTo(GeneratedMediaKind.FILE);
        assertThat(media.fileName()).isEqualTo("legacy.docx");
        assertThat(media.bytes()).isNotEmpty();
    }

    @Test
    void rejectsUnsafeFileName() {
        DocumentGenerationCapability capability = capability();
        DocumentRequest request = new DocumentRequest(
                "title",
                List.of(new DocumentSection.ParagraphSection("x")));

        assertThatThrownBy(() -> capability.generate("../bad.docx", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingExtension() {
        DocumentGenerationCapability capability = capability();
        DocumentRequest request = new DocumentRequest(
                "title",
                List.of(new DocumentSection.ParagraphSection("x")));

        assertThatThrownBy(() -> capability.generate("report", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedExtension() {
        DocumentGenerationCapability capability = capability();
        DocumentRequest request = new DocumentRequest(
                "title",
                List.of(new DocumentSection.ParagraphSection("x")));

        assertThatThrownBy(() -> capability.generate("report.rtf", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DocumentGenerationCapability capability() {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(outputDirectory);
        DocumentRenderer renderer = new DocumentRenderer(properties);
        WordDocumentClient wordClient = new WordDocumentClient(renderer, properties);
        return new DocumentGenerationCapability(wordClient, new PdfDocumentClient(properties), properties);
    }
}