package com.weida.bot.wechatilinkbot.model;

import java.util.Objects;

/**
 * Converted video bytes and playback duration.
 *
 * @param bytes MP4 bytes
 * @param durationMillis playback duration
 */
public record ConvertedVideo(byte[] bytes, Long durationMillis) {

    public ConvertedVideo {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("视频内容不能为空");
        }
        Objects.requireNonNull(durationMillis, "durationMillis 不能为空");
        if (durationMillis <= 0L) {
            throw new IllegalArgumentException("durationMillis 必须大于 0");
        }
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
