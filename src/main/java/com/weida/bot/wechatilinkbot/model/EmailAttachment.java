package com.weida.bot.wechatilinkbot.model;

import java.util.Objects;

/**
 * Immutable in-memory email attachment.
 *
 * @param fileName safe attachment name
 * @param contentType MIME type
 * @param bytes attachment bytes
 */
public record EmailAttachment(
        String fileName,
        String contentType,
        byte[] bytes) {

    public EmailAttachment {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("附件文件名不能为空");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("附件类型不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("附件内容不能为空");
        }
        fileName = fileName.strip();
        contentType = contentType.strip();
        bytes = bytes.clone();
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(contentType);
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
