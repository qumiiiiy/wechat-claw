package com.weida.bot.wechatilinkbot.channel.ilink;

import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stores optional inbound WeChat media under a normalized, confined local path.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class IlinkMediaStorage {

    private static final Logger LOG = LoggerFactory.getLogger(IlinkMediaStorage.class);
    private static final Pattern UNSAFE_FILE_CHARACTER_PATTERN =
            Pattern.compile("[\\\\/:*?\"<>|]");

    private final IlinkProperties properties;

    public IlinkMediaStorage(IlinkProperties properties) {
        this.properties = properties;
    }

    /**
     * Stores media when local media retention is enabled.
     *
     * @param botId source Bot identifier
     * @param message source message
     * @param itemIndex source item index
     * @param suggestedFileName untrusted SDK file name
     * @param bytes downloaded media bytes
     */
    public void store(
            String botId,
            WeixinMessage message,
            int itemIndex,
            String suggestedFileName,
            byte[] bytes) {
        if (!properties.isMediaDownloadEnabled()) {
            return;
        }
        try {
            Path target = targetPath(botId, message, itemIndex, suggestedFileName);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            LOG.info(
                    "wechat_media_saved messageRef={} size={}",
                    IlinkLogReference.of(message.getMessage_id()),
                    bytes.length);
        } catch (IOException | RuntimeException exception) {
            LOG.error(
                    "wechat_media_save_failed messageRef={} itemIndex={}",
                    IlinkLogReference.of(message.getMessage_id()),
                    itemIndex,
                    exception);
        }
    }

    /**
     * Removes path separators and Windows-reserved path characters from a file name.
     *
     * @param value untrusted name
     * @param fallback fallback when the name is absent or special
     * @return one safe path segment
     */
    public static String sanitizeFileName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String sanitized = UNSAFE_FILE_CHARACTER_PATTERN.matcher(value).replaceAll("_");
        return ".".equals(sanitized) || "..".equals(sanitized) ? fallback : sanitized;
    }

    private Path targetPath(
            String botId,
            WeixinMessage message,
            int itemIndex,
            String suggestedFileName) throws IOException {
        Path rootDirectory = properties.getMediaDownloadDirectory()
                .toAbsolutePath()
                .normalize();
        String safeBotId = sanitizeFileName(botId, "unknown-bot");
        Path directory = rootDirectory.resolve(safeBotId).normalize();
        if (!directory.startsWith(rootDirectory)) {
            throw new IOException("非法 Bot 媒体目录");
        }
        String fileName = message.getMessage_id()
                + "-"
                + itemIndex
                + "-"
                + sanitizeFileName(suggestedFileName, "media.bin");
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) {
            throw new IOException("非法媒体文件路径");
        }
        return target;
    }
}
