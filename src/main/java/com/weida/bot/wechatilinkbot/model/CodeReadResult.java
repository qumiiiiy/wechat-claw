package com.weida.bot.wechatilinkbot.model;

import java.util.List;

/**
 * Bounded read result from a code file.
 *
 * @param relativePath path relative to the repository root
 * @param fileId opaque stable identifier for the file
 * @param lines requested line content
 * @param startLine 1-based start line number
 * @param totalLines total line count in the file
 * @param truncated whether the content was truncated by the line limit
 */
public record CodeReadResult(
        String relativePath,
        String fileId,
        List<String> lines,
        int startLine,
        int totalLines,
        boolean truncated) {
}
