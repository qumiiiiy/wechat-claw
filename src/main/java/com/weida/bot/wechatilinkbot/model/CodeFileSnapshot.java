package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;
import java.util.List;

/**
 * Trusted snapshot of a code file at a point in time.
 *
 * @param relativePath path relative to the repository root
 * @param fileId opaque stable identifier
 * @param lines file content as line list
 * @param totalLines total line count
 * @param sizeBytes file size in bytes
 * @param lastModifiedTime last modification time
 */
public record CodeFileSnapshot(
        String relativePath,
        String fileId,
        List<String> lines,
        int totalLines,
        long sizeBytes,
        Instant lastModifiedTime) {
}
