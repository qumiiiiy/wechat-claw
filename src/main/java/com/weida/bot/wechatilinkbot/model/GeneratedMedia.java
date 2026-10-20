package com.weida.bot.wechatilinkbot.model;

import java.util.Objects;

/**
 * Generated media ready for delivery through a channel adapter.
 *
 * @param kind media kind
 * @param bytes media bytes
 * @param fileName safe file name
 * @param durationMillis playback duration, or zero for non-timed media
 * @param caption optional channel caption
 */
public record GeneratedMedia(
        GeneratedMediaKind kind,
        byte[] bytes,
        String fileName,
        Long durationMillis,
        String caption) {

    public GeneratedMedia {
        Objects.requireNonNull(kind, "kind 不能为空");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("媒体内容不能为空");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName 不能为空");
        }
        Objects.requireNonNull(durationMillis, "durationMillis 不能为空");
        if (durationMillis < 0L) {
            throw new IllegalArgumentException("durationMillis 不能为负数");
        }
        bytes = bytes.clone();
        fileName = fileName.strip();
        caption = caption == null ? "" : caption.strip();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
