package com.weida.bot.wechatilinkbot.model;

import java.util.Objects;

/**
 * A validated visual attachment passed from a channel to the agent.
 *
 * @param kind media kind
 * @param mimeType validated MIME type
 * @param bytes attachment bytes
 */
public record MediaPart(MediaKind kind, String mimeType, byte[] bytes) {

    public MediaPart {
        Objects.requireNonNull(kind, "kind 不能为空");
        if (mimeType == null || mimeType.isBlank()) {
            throw new IllegalArgumentException("mimeType 不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("媒体内容不能为空");
        }
        mimeType = mimeType.strip();
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
