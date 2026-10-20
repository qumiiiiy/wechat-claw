package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class DocumentCapabilityTest {

    @Test
    void extractsUtf8TextFileContent() {
        DocumentCapability capability = capability();

        String content = capability.extract(
                "第一行\n\n第二行：中文内容".getBytes(StandardCharsets.UTF_8),
                "说明.txt");

        assertThat(content).contains("第一行", "第二行：中文内容");
    }

    @Test
    void extractsTextLayerFromPdf() throws IOException {
        DocumentCapability capability = capability();

        String content = capability.extract(pdfBytes("PDF document content 2026"), "guide.pdf");

        assertThat(content).contains("PDF document content 2026");
    }

    @Test
    void rejectsDocumentsExceedingTheConfiguredByteLimit() {
        DocumentProperties properties = new DocumentProperties();
        properties.setMaxBytes(1024);
        DocumentCapability capability = new DocumentCapability(properties);

        String content = capability.extract(new byte[1025], "large.txt");

        assertThat(content).isEmpty();
    }

    private static DocumentCapability capability() {
        return new DocumentCapability(new DocumentProperties());
    }

    private static byte[] pdfBytes(String text) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
