package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.code.CodeFileReader;
import com.weida.bot.wechatilinkbot.infrastructure.code.CodeRepositoryScanner;
import com.weida.bot.wechatilinkbot.infrastructure.code.CodeWorkspaceProperties;
import com.weida.bot.wechatilinkbot.infrastructure.code.PatchApplier;
import com.weida.bot.wechatilinkbot.infrastructure.code.PatchConfirmationStore;
import com.weida.bot.wechatilinkbot.infrastructure.code.TestRunner;
import com.weida.bot.wechatilinkbot.infrastructure.code.UnifiedDiffGenerator;
import com.weida.bot.wechatilinkbot.model.CodeFileSnapshot;
import com.weida.bot.wechatilinkbot.model.CodeReadResult;
import com.weida.bot.wechatilinkbot.model.CodeSearchResult;
import com.weida.bot.wechatilinkbot.model.DiffResult;
import com.weida.bot.wechatilinkbot.model.PatchApplyResult;
import com.weida.bot.wechatilinkbot.model.PatchConfirmation;
import com.weida.bot.wechatilinkbot.model.TestResult;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Secure code workspace capability.
 *
 * <p>Provides bounded search, read, diff generation, patch confirmation,
 * safe patch application, and fixed test execution within whitelisted
 * code repositories.</p>
 */
@Component
public class CodeWorkspaceCapability {

    private final CodeWorkspaceProperties properties;
    private final CodeRepositoryScanner scanner;
    private final CodeFileReader reader;
    private final UnifiedDiffGenerator diffGenerator;
    private final PatchConfirmationStore confirmationStore;
    private final PatchApplier patchApplier;
    private final TestRunner testRunner;

    public CodeWorkspaceCapability(
            CodeWorkspaceProperties properties,
            CodeRepositoryScanner scanner,
            CodeFileReader reader,
            UnifiedDiffGenerator diffGenerator,
            PatchConfirmationStore confirmationStore,
            PatchApplier patchApplier,
            TestRunner testRunner) {
        this.properties = properties;
        this.scanner = scanner;
        this.reader = reader;
        this.diffGenerator = diffGenerator;
        this.confirmationStore = confirmationStore;
        this.patchApplier = patchApplier;
        this.testRunner = testRunner;
    }

    public boolean available() {
        return properties.isEnabled()
                && properties.getAllowedRepositories() != null
                && !properties.getAllowedRepositories().isEmpty();
    }

    /** Searches allowed repositories for files matching a keyword. */
    public List<CodeSearchResult> search(String keyword) {
        requireAvailable();
        return scanner.search(keyword);
    }

    /** Reads a file with bounded output. */
    public CodeReadResult read(String repoPath, String relativePath,
                               Integer startLine, Integer lineCount) {
        requireAvailable();
        Path repoRoot = resolveRepoRoot(repoPath);
        return reader.read(repoRoot, relativePath, startLine, lineCount);
    }

    /** Takes a full snapshot of a file for diff generation. */
    public CodeFileSnapshot snapshot(String repoPath, String relativePath) {
        requireAvailable();
        Path repoRoot = resolveRepoRoot(repoPath);
        return reader.snapshot(repoRoot, relativePath);
    }

    /** Generates a unified diff between snapshot and new content. */
    public DiffResult generateDiff(String repoPath, String relativePath,
                                   List<String> newLines) {
        requireAvailable();
        Path repoRoot = resolveRepoRoot(repoPath);
        CodeFileSnapshot snap = reader.snapshot(repoRoot, relativePath);
        return diffGenerator.generate(snap, newLines);
    }

    /** Creates a patch confirmation bound to a conversation. */
    public PatchConfirmation createPatchConfirmation(
            String conversationId,
            String repoPath,
            String relativePath,
            List<String> newLines) {
        requireAvailable();
        Path repoRoot = resolveRepoRoot(repoPath);
        CodeFileSnapshot snap = reader.snapshot(repoRoot, relativePath);
        DiffResult diff = diffGenerator.generate(snap, newLines);
        return confirmationStore.create(conversationId, snap.fileId(), snap, diff.diff());
    }

    /** Applies a patch using a confirmation ID. */
    public PatchApplyResult applyPatch(
            String confirmationId,
            String conversationId,
            String repoPath) {
        requireAvailable();
        Path repoRoot = resolveRepoRoot(repoPath);
        return patchApplier.apply(confirmationId, conversationId, repoRoot);
    }

    /** Runs the pre-configured test command. */
    public TestResult runTests(String repoPath) {
        requireAvailable();
        Path repoRoot = resolveRepoRoot(repoPath);
        return testRunner.run(repoRoot);
    }

    /** Clears all pending confirmations for a conversation. */
    public void clearConversation(String conversationId) {
        confirmationStore.clearConversation(conversationId);
    }

    private void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("代码工作区功能未启用或未配置允许仓库");
        }
    }

    private Path resolveRepoRoot(String repoPath) {
        if (repoPath == null || repoPath.isBlank()) {
            throw new IllegalArgumentException("仓库路径不能为空");
        }
        Path normalized = Path.of(repoPath.strip()).toAbsolutePath().normalize();
        for (String allowed : properties.getAllowedRepositories()) {
            Path allowedPath = Path.of(allowed.strip()).toAbsolutePath().normalize();
            if (normalized.equals(allowedPath) || normalized.startsWith(allowedPath)) {
                return normalized;
            }
        }
        throw new IllegalArgumentException("仓库路径不在允许列表中");
    }
}
