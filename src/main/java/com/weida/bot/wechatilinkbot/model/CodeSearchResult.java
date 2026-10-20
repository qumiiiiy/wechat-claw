package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;

/**
 * One file match from a code repository search.
 *
 * @param relativePath path relative to the repository root
 * @param fileId opaque stable identifier for the file
 * @param sizeBytes file size in bytes
 * @param lastModifiedTime last modification time
 */
public record CodeSearchResult(
        String relativePath,
        String fileId,
        long sizeBytes,
        Instant lastModifiedTime) {
}
