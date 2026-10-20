package com.weida.bot.wechatilinkbot.model;

/**
 * Result of generating a unified diff for a file.
 *
 * @param relativePath path relative to the repository root
 * @param fileId the file this diff is for
 * @param diff unified diff content
 * @param hunks number of diff hunks
 * @param lines total diff lines
 * @param truncated whether the diff was truncated
 */
public record DiffResult(
        String relativePath,
        String fileId,
        String diff,
        int hunks,
        int lines,
        boolean truncated) {
}
