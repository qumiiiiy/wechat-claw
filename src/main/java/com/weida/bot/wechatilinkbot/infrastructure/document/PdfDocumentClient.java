package com.weida.bot.wechatilinkbot.infrastructure.document;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Generates PDF documents using Apache PDFBox with Chinese font support.
 *
 * @author ryx
 * @since 2026-07-24
 */
@Component
public class PdfDocumentClient {

    private static final Logger LOG = LoggerFactory.getLogger(PdfDocumentClient.class);
    private static final float MARGIN = 50;
    private static final float FONT_SIZE = 12;
    private static final float TITLE_FONT_SIZE = 18;
    private static final float LINE_HEIGHT = 20;

    private static final String[] CHINESE_FONT_PATHS = {
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/kaiti.ttf",
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/System/Library/Fonts/STHeiti Light.ttc"
    };

    private final DocumentGenerationProperties properties;

    public PdfDocumentClient(DocumentGenerationProperties properties) {
        this.properties = properties;
    }

    public boolean isAvailable() {
        return properties.isEnabled();
    }

    public byte[] generate(String title, String content) throws IOException {
        if (!isAvailable()) {
            throw new IOException("文档生成服务当前不可用");
        }
        if (content == null || content.isBlank()) {
            throw new IOException("文档内容不能为空");
        }
        if (content.length() > properties.getMaxContentCharacters()) {
            throw new IOException("文档内容超过最大字符数限制：" + properties.getMaxContentCharacters());
        }

        try (PDDocument document = new PDDocument()) {
            PDFont font = loadChineseFont(document);

            List<String> lines = new ArrayList<>();
            if (title != null && !title.isBlank()) {
                lines.add(title);
                lines.add("");
            }
            for (String line : content.split("\n")) {
                lines.add(line);
            }

            PDPage currentPage = new PDPage(PDRectangle.A4);
            document.addPage(currentPage);
            PDPageContentStream contentStream = new PDPageContentStream(document, currentPage);
            contentStream.beginText();
            contentStream.setFont(font, FONT_SIZE);
            contentStream.setLeading(LINE_HEIGHT);
            contentStream.newLineAtOffset(MARGIN, currentPage.getMediaBox().getHeight() - MARGIN);

            float yPosition = currentPage.getMediaBox().getHeight() - MARGIN;
            boolean isTitle = true;

            for (String line : lines) {
                if (yPosition < MARGIN + LINE_HEIGHT) {
                    contentStream.endText();
                    contentStream.close();

                    currentPage = new PDPage(PDRectangle.A4);
                    document.addPage(currentPage);
                    contentStream = new PDPageContentStream(document, currentPage);
                    contentStream.beginText();
                    contentStream.setFont(font, FONT_SIZE);
                    contentStream.setLeading(LINE_HEIGHT);
                    contentStream.newLineAtOffset(MARGIN, currentPage.getMediaBox().getHeight() - MARGIN);
                    yPosition = currentPage.getMediaBox().getHeight() - MARGIN;
                }

                if (isTitle && title != null && !title.isBlank()) {
                    contentStream.setFont(font, TITLE_FONT_SIZE);
                    contentStream.showText(line);
                    contentStream.newLine();
                    contentStream.setFont(font, FONT_SIZE);
                    isTitle = false;
                } else {
                    contentStream.showText(line);
                    contentStream.newLine();
                }
                yPosition -= LINE_HEIGHT;
            }

            contentStream.endText();
            contentStream.close();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            byte[] bytes = outputStream.toByteArray();

            if (bytes.length > properties.getMaxContentBytes()) {
                throw new IOException("生成的PDF超过最大字节数限制：" + properties.getMaxContentBytes());
            }

            LOG.info("pdf_document_generated title={} bytes={}", title, bytes.length);
            return bytes;
        }
    }

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        for (String fontPath : CHINESE_FONT_PATHS) {
            try {
                File fontFile = new File(fontPath);
                if (!fontFile.exists() || !fontFile.canRead()) {
                    continue;
                }

                try (InputStream fontStream = new FileInputStream(fontFile)) {
                    PDFont font = PDType0Font.load(document, fontStream, true);
                    LOG.info("chinese_font_loaded path={} name={}", fontPath, font.getName());
                    return font;
                }
            } catch (Exception e) {
                LOG.debug("font_load_failed path={} error={}", fontPath, e.getMessage());
            }
        }

        throw new IOException("未找到支持中文的字体文件，请确保系统已安装中文字体（如黑体、宋体、微软雅黑）");
    }
}
