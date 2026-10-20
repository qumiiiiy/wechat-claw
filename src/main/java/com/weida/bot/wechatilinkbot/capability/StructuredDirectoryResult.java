package com.weida.bot.wechatilinkbot.capability;

/** Bounded, presentation-neutral metadata for one directory listing entry. */
public record StructuredDirectoryResult(
        int naturalIndex,
        String displayName,
        String relativePath) {

    public StructuredDirectoryResult {
        if (naturalIndex < 1) {
            throw new IllegalArgumentException("目录序号必须大于 0");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("目录名不能为空");
        }
        if (relativePath == null || relativePath.isBlank() || relativePath.contains("..")) {
            throw new IllegalArgumentException("目录相对路径无效");
        }
    }
}
