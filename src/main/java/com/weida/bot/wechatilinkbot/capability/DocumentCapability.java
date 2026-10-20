package com.weida.bot.wechatilinkbot.capability;

import java.io.ByteArrayInputStream;
import java.util.regex.Pattern;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts bounded plain text from supported document payloads through Apache Tika.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class DocumentCapability {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentCapability.class);
    private static final Pattern INLINE_WHITESPACE_PATTERN = Pattern.compile("[\\t\\x0B\\f ]+");
    private static final Pattern BLANK_LINE_PATTERN = Pattern.compile("(?m)^\\s+$");
    private static final Pattern EXCESSIVE_NEWLINE_PATTERN = Pattern.compile("\\n{3,}");

    private final Tika tika = new Tika();
    private final DocumentProperties properties;

    public DocumentCapability(DocumentProperties properties) {
        this.properties = properties;
    }

    /**
     * Extracts normalized text without propagating parser-specific failures.
     *
     * @param bytes document payload
     * @param fileName safe resource name for format detection
     * @return normalized text, or an empty string when extraction is unavailable
     */
    public String extract(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (bytes.length > properties.getMaxBytes()) {
            LOG.warn(
                    "wechat_document_skipped_too_large fileName={} size={} maxSize={}",
                    fileName,
                    bytes.length,
                    properties.getMaxBytes());
            return "";
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            Metadata metadata = new Metadata();
            if (fileName != null && !fileName.isBlank()) {
                metadata.set("resourceName", fileName);
            }
            String extractedText = tika.parseToString(
                    input, metadata, properties.getMaxCharacters());
            String normalizedText = normalize(extractedText);
            LOG.info(
                    "wechat_document_extracted fileName={} size={} extractedCharacters={}",
                    fileName,
                    bytes.length,
                    normalizedText.length());
            return normalizedText;
        } catch (Exception exception) {
            LOG.warn(
                    "wechat_document_extract_failed fileName={} size={} reason={}",
                    fileName,
                    bytes.length,
                    exception.getMessage());
            return "";
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutNull = value.replace('\u0000', ' ');
        String compactWhitespace = INLINE_WHITESPACE_PATTERN
                .matcher(withoutNull)
                .replaceAll(" ");
        String withoutBlankLines = BLANK_LINE_PATTERN
                .matcher(compactWhitespace)
                .replaceAll("");
        return EXCESSIVE_NEWLINE_PATTERN
                .matcher(withoutBlankLines)
                .replaceAll("\n\n")
                .strip();
    }
}
