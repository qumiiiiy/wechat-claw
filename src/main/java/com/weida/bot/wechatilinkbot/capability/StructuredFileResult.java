package com.weida.bot.wechatilinkbot.capability;

import java.time.Instant;
import java.util.Objects;

/** Bounded, presentation-neutral metadata for one file search hit. */
public record StructuredFileResult(
        int naturalIndex,
        String displayName,
        String relativeDirectory,
        long sizeBytes,
        String humanReadableSize,
        Instant lastModifiedTime,
        String fileId) {

    public StructuredFileResult {
        if (naturalIndex < 1) {
            throw new IllegalArgumentException("文件序号必须大于 0");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (relativeDirectory == null || relativeDirectory.contains("..")) {
            throw new IllegalArgumentException("文件相对目录无效");
        }
        if (sizeBytes < 0L) {
            throw new IllegalArgumentException("文件大小不能为负数");
        }
        Objects.requireNonNull(lastModifiedTime, "修改时间不能为空");
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("文件引用不能为空");
        }
        humanReadableSize = humanReadableSize == null ? "" : humanReadableSize.strip();
    }
}
