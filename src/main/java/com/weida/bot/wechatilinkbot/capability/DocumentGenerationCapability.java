package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.document.DocumentGenerationProperties;
import com.weida.bot.wechatilinkbot.infrastructure.document.PdfDocumentClient;
import com.weida.bot.wechatilinkbot.infrastructure.document.WordDocumentClient;
import com.weida.bot.wechatilinkbot.model.DocumentEditRequest;
import com.weida.bot.wechatilinkbot.model.DocumentEditResult;
import com.weida.bot.wechatilinkbot.model.DocumentRequest;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Document generation capability for Word and PDF formats.
 *
 * <p>DOCX generation now flows through the structured
 * {@link DocumentRequest} path; the legacy plain-text overload is preserved
 * for backward compatibility and is internally mapped to a sequence of
 * paragraph sections. PDF generation continues to operate on plain text
 * only.</p>
 *
 * @author ryx
 * @since 2026-07-24
 */
@Component
public class DocumentGenerationCapability {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("docx", "pdf");
    private static final Pattern SAFE_FILE_NAME =
            Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}._-]*");
    private static final Pattern WINDOWS_DEVICE_NAME = Pattern.compile(
            "(?i)(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?");

    private final WordDocumentClient wordClient;
    private final PdfDocumentClient pdfClient;
    private final DocumentGenerationProperties properties;

    public DocumentGenerationCapability(
            WordDocumentClient wordClient,
            PdfDocumentClient pdfClient,
            DocumentGenerationProperties properties) {
        this.wordClient = wordClient;
        this.pdfClient = pdfClient;
        this.properties = properties;
    }

    public boolean available() {
        return properties.isEnabled() && (wordClient.isAvailable() || pdfClient.isAvailable());
    }

    /**
     * Generates a DOCX document from the structured request. The server-owned
     * output filesystem is anchored at
     * {@link DocumentGenerationProperties#getOutputDirectory()}; the file name
     * is derived from the request title inside this capability and is never
     * controlled by the model.
     */
    public GeneratedMedia generate(String fileName, DocumentRequest request) {
        Objects.requireNonNull(request, "request");
        String validatedFileName = validateFileName(fileName);
        String extension = validatedFileName
                .substring(validatedFileName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        if (!"docx".equals(extension)) {
            throw new IllegalArgumentException(
                    "结构化入口仅支持 .docx 文件名，实际：" + extension);
        }
        WordDocumentClient.DocumentGenerationResult result = wordClient.generate(request);
        return new GeneratedMedia(
                GeneratedMediaKind.FILE,
                result.bytes(),
                result.fileName(),
                0L,
                "");
    }

    /**
     * Backward-compatible plain-text entry point. Delegates to the
     * appropriate client based on the file extension.
     */
    public GeneratedMedia generate(String fileName, String title, String content) throws IOException {
        String validatedFileName = validateFileName(fileName);
        String extension = validatedFileName
                .substring(validatedFileName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);

        String normalizedContent = normalizeContent(content);

        byte[] bytes;
        if ("docx".equals(extension)) {
            bytes = wordClient.generate(title, normalizedContent);
        } else if ("pdf".equals(extension)) {
            bytes = pdfClient.generate(title, normalizedContent);
        } else {
            throw new IOException("不支持的文件格式：" + extension);
        }

        return new GeneratedMedia(GeneratedMediaKind.FILE, bytes, validatedFileName, 0L, "");
    }

    /**
     * Loads the DOCX at the server-resolved path, applies the narrow edits
     * declared by {@link DocumentEditRequest}, and returns the modified
     * copy as a {@link GeneratedMedia}. The source file is never mutated;
     * the returned record carries the source SHA-256 so callers can
     * confirm byte-equality of the original if needed.
     */
    public GeneratedMedia edit(Path trustedSourcePath, DocumentEditRequest request) {
        Objects.requireNonNull(trustedSourcePath, "trustedSourcePath 不能为空");
        Objects.requireNonNull(request, "request 不能为空");
        if (!properties.isEnabled() || !wordClient.isAvailable()) {
            throw new IllegalStateException("文档生成未启用");
        }
        DocumentEditResult result = wordClient.edit(trustedSourcePath, request);
        return new GeneratedMedia(
                GeneratedMediaKind.FILE,
                result.bytes(),
                result.fileName(),
                0L,
                "");
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        return content.replace("\\n", "\n");
    }

    private String validateFileName(String rawFileName) {
        if (rawFileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String fileName = Normalizer.normalize(rawFileName.strip(), Normalizer.Form.NFKC);
        if (fileName.isBlank() || fileName.length() > properties.getMaxFileNameCharacters()) {
            throw new IllegalArgumentException("文件名为空或超过长度限制");
        }
        if (!SAFE_FILE_NAME.matcher(fileName).matches()
                || fileName.contains("..")
                || WINDOWS_DEVICE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("文件名不安全，不能包含路径、控制字符或保留名称");
        }
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == fileName.length() - 1) {
            throw new IllegalArgumentException("文件名必须包含受支持的扩展名（.docx 或 .pdf）");
        }
        String extension = fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "不支持的文件扩展名：" + extension + "，仅支持 .docx 和 .pdf");
        }
        return fileName;
    }
}