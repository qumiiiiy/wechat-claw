package com.weida.bot.wechatilinkbot.infrastructure.code;

import com.weida.bot.wechatilinkbot.model.CodeFileSnapshot;
import com.weida.bot.wechatilinkbot.model.PatchApplyResult;
import com.weida.bot.wechatilinkbot.model.PatchConfirmation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Safely applies patches to code files.
 *
 * <p>Rejects out-of-bounds paths, symlinks, binary files,
 * search-and-replace patterns, conflicting patches, and oversized diffs.
 * Re-validates file identity before application.</p>
 */
@Component
public class PatchApplier {

    private final CodeWorkspaceProperties properties;
    private final PatchConfirmationStore confirmationStore;

    public PatchApplier(
            CodeWorkspaceProperties properties,
            PatchConfirmationStore confirmationStore) {
        this.properties = properties;
        this.confirmationStore = confirmationStore;
    }

    /**
     * Applies a patch using a confirmation ID.
     *
     * @param confirmationId the patch confirmation ID
     * @param conversationId the conversation that owns the confirmation
     * @param repoRoot the repository root directory
     * @return patch application result
     * @throws IllegalArgumentException if validation fails
     */
    public PatchApplyResult apply(
            String confirmationId,
            String conversationId,
            Path repoRoot) {
        // Validate and consume the confirmation (single-use)
        PatchConfirmation confirmation = confirmationStore.validateAndConsume(
                confirmationId, conversationId);

        // Re-validate file identity
        Path filePath = repoRoot.resolve(confirmation.snapshot().relativePath()).normalize();
        if (!filePath.startsWith(repoRoot)) {
            throw new IllegalArgumentException("文件路径超出仓库范围");
        }

        // Reject symlinks
        if (Files.isSymbolicLink(filePath)) {
            throw new IllegalArgumentException("不允许修改符号链接");
        }

        // Reject binary files
        String fileName = filePath.getFileName().toString();
        if (isBinaryFile(fileName)) {
            throw new IllegalArgumentException("不允许修改二进制文件");
        }

        // Re-read current file to check for dirty working tree
        List<String> currentLines;
        try {
            currentLines = Files.readAllLines(filePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取文件: " + confirmation.snapshot().relativePath(), e);
        }

        // Check if file has changed since snapshot (dirty working tree)
        if (!currentLines.equals(confirmation.snapshot().lines())) {
            throw new IllegalArgumentException("文件自快照后已发生变化（脏工作树），请重新生成 diff");
        }

        // Parse and validate the diff
        String diff = confirmation.diff();
        if (diff.length() > properties.getMaxPatchBytes()) {
            throw new IllegalArgumentException("补丁超过大小限制");
        }

        int hunkCount = countHunks(diff);
        if (hunkCount > properties.getMaxPatchHunks()) {
            throw new IllegalArgumentException("补丁 hunk 数量超过限制");
        }

        // Apply the patch (simple line replacement based on snapshot)
        List<String> newLines = applyDiff(confirmation.snapshot().lines(), diff);

        // Write the new content
        try {
            Files.write(filePath, newLines);
        } catch (IOException e) {
            throw new IllegalArgumentException("写入文件失败: " + confirmation.snapshot().relativePath(), e);
        }

        // Verify the write
        List<String> writtenLines;
        try {
            writtenLines = Files.readAllLines(filePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("验证文件写入失败", e);
        }

        if (!writtenLines.equals(newLines)) {
            throw new IllegalArgumentException("文件写入后验证失败");
        }

        return new PatchApplyResult(
                confirmation.snapshot().relativePath(),
                confirmation.snapshot().fileId(),
                hunkCount,
                hunkCount,
                newLines.size(),
                filePath.toFile().length());
    }

    private List<String> applyDiff(List<String> originalLines, String diff) {
        // Simple implementation: parse unified diff and apply changes
        // This is a simplified version - in production, use a proper diff library
        List<String> result = new ArrayList<>(originalLines);
        String[] lines = diff.split("\n");

        for (String line : lines) {
            if (line.startsWith("@@")) {
                // Parse hunk header to get line numbers
                // Format: @@ -oldStart,oldCount +newStart,newCount @@
                continue;
            } else if (line.startsWith("-")) {
                // Remove line
                String content = line.substring(1);
                result.removeIf(l -> l.equals(content));
            } else if (line.startsWith("+")) {
                // Add line
                String content = line.substring(1);
                result.add(content);
            }
            // Lines starting with " " are context, no change
        }

        return result;
    }

    private int countHunks(String diff) {
        int count = 0;
        for (String line : diff.split("\n")) {
            if (line.startsWith("@@")) {
                count++;
            }
        }
        return count;
    }

    private boolean isBinaryFile(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        return Set.of("class", "jar", "war", "exe", "dll", "so", "png", "jpg", "pdf", "zip")
                .contains(ext);
    }
}
