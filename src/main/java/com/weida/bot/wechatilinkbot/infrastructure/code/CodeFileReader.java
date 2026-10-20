package com.weida.bot.wechatilinkbot.infrastructure.code;

import com.weida.bot.wechatilinkbot.model.CodeFileSnapshot;
import com.weida.bot.wechatilinkbot.model.CodeReadResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Reads code files with bounded output.
 *
 * <p>Returns relative path, line numbers, and truncation status.
 * Never reads beyond configured limits. Rejects binary files,
 * symlinks, and sensitive paths.</p>
 */
@Component
public class CodeFileReader {

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "class", "jar", "war", "ear", "so", "dll", "dylib",
            "exe", "bin", "obj", "o", "a", "lib",
            "png", "jpg", "jpeg", "gif", "bmp", "ico",
            "pdf", "zip", "tar", "gz", "bz2", "xz", "7z",
            "db", "sqlite", "sqlite3");

    private static final Set<String> SENSITIVE_DIRS = Set.of(
            ".git", ".svn", ".hg", ".bzr", "node_modules",
            ".venv", "venv", "__pycache__", ".idea", ".vscode");

    private static final Set<String> SENSITIVE_FILES = Set.of(
            ".env", ".env.local", ".env.production",
            "id_rsa", "id_ed25519", "id_dsa", "id_ecdsa",
            "known_hosts", "authorized_keys",
            ".gitconfig", ".npmrc", ".pypirc");

    private final CodeWorkspaceProperties properties;

    public CodeFileReader(CodeWorkspaceProperties properties) {
        this.properties = properties;
    }

    /**
     * Reads a file with bounded output.
     *
     * @param repoRoot the repository root directory
     * @param relativePath path relative to the repository root
     * @param startLine 1-based start line (null means from beginning)
     * @param lineCount number of lines to read (null means up to maxReadLines)
     * @return bounded read result with truncation status
     * @throws IllegalArgumentException if the file is invalid or inaccessible
     */
    public CodeReadResult read(
            Path repoRoot,
            String relativePath,
            Integer startLine,
            Integer lineCount) {
        validateRelativePath(relativePath);
        Path filePath = repoRoot.resolve(relativePath).normalize();

        // Ensure the file is within the repository root
        if (!filePath.startsWith(repoRoot)) {
            throw new IllegalArgumentException("文件路径超出仓库范围");
        }

        // Reject symlinks
        if (Files.isSymbolicLink(filePath)) {
            throw new IllegalArgumentException("不允许读取符号链接");
        }

        // Reject binary files
        if (isBinaryFile(filePath.getFileName().toString())) {
            throw new IllegalArgumentException("不允许读取二进制文件");
        }

        // Reject sensitive files
        if (isSensitivePath(repoRoot, filePath)) {
            throw new IllegalArgumentException("不允许读取敏感文件");
        }

        // Read file attributes
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(filePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new IllegalArgumentException("无法访问文件: " + relativePath, e);
        }

        if (!attrs.isRegularFile()) {
            throw new IllegalArgumentException("目标不是普通文件");
        }
        if (attrs.size() > properties.getMaxReadBytes()) {
            throw new IllegalArgumentException("文件超过读取大小限制");
        }

        // Read file content with bounds
        List<String> allLines;
        try {
            allLines = Files.readAllLines(filePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("读取文件失败: " + relativePath, e);
        }

        int totalLines = allLines.size();
        int fromLine = startLine != null ? Math.max(1, startLine) : 1;
        int maxLines = lineCount != null
                ? Math.min(lineCount, properties.getMaxReadLines())
                : properties.getMaxReadLines();

        // Clamp to file bounds
        if (fromLine > totalLines) {
            fromLine = totalLines;
        }

        int toLine = Math.min(fromLine + maxLines - 1, totalLines);
        List<String> lines = allLines.subList(fromLine - 1, toLine);
        boolean truncated = toLine < totalLines;

        String fileId = "code-" + relativePath.hashCode();

        return new CodeReadResult(
                relativePath,
                fileId,
                List.copyOf(lines),
                fromLine,
                totalLines,
                truncated);
    }

    /**
     * Creates a full snapshot of a file for diff generation.
     */
    public CodeFileSnapshot snapshot(Path repoRoot, String relativePath) {
        CodeReadResult readResult = read(repoRoot, relativePath, null, null);
        List<String> allLines;
        try {
            Path filePath = repoRoot.resolve(relativePath).normalize();
            allLines = Files.readAllLines(filePath);
        } catch (IOException e) {
            throw new IllegalArgumentException("读取文件失败: " + relativePath, e);
        }

        Path filePath = repoRoot.resolve(relativePath).normalize();
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(filePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new IllegalArgumentException("无法获取文件属性: " + relativePath, e);
        }

        String fileId = "code-" + relativePath.hashCode();

        return new CodeFileSnapshot(
                relativePath,
                fileId,
                List.copyOf(allLines),
                allLines.size(),
                attrs.size(),
                attrs.lastModifiedTime().toInstant());
    }

    private void validateRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("相对路径不能为空");
        }
        // Reject absolute paths
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            throw new IllegalArgumentException("不允许使用绝对路径");
        }
        // Reject path traversal
        if (relativePath.contains("..")) {
            throw new IllegalArgumentException("不允许路径遍历");
        }
    }

    private boolean isBinaryFile(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return BINARY_EXTENSIONS.contains(ext);
    }

    private boolean isSensitivePath(Path repoRoot, Path filePath) {
        Path relative = repoRoot.relativize(filePath);
        for (Path component : relative) {
            String name = component.toString().toLowerCase(Locale.ROOT);
            if (SENSITIVE_DIRS.contains(name) || SENSITIVE_FILES.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
