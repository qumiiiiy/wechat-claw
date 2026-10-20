package com.weida.bot.wechatilinkbot.model;

/**
 * Result of applying a patch to a file.
 *
 * @param relativePath path relative to the repository root
 * @param fileId the file this patch was applied to
 * @param hunksApplied number of hunks successfully applied
 * @param hunksTotal total number of hunks in the patch
 * @param newLines number of lines after patch
 * @param newSizeBytes file size after patch in bytes
 */
public record PatchApplyResult(
        String relativePath,
        String fileId,
        int hunksApplied,
        int hunksTotal,
        int newLines,
        long newSizeBytes) {
}
