package com.weida.bot.wechatilinkbot.infrastructure.code;

import com.weida.bot.wechatilinkbot.model.CodeSearchResult;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Scans allowed code repositories for files matching a keyword.
 *
 * <p>Enforces repository whitelist, depth limit, result limit,
 * and rejects sensitive paths and symlinks.</p>
 */
@Component
public class CodeRepositoryScanner {

    private static final Set<String> SENSITIVE_DIRS = Set.of(
            ".git", ".svn", ".hg", ".bzr", "node_modules",
            ".venv", "venv", "__pycache__", ".idea", ".vscode");

    private static final Set<String> SENSITIVE_FILES = Set.of(
            ".env", ".env.local", ".env.production",
            "id_rsa", "id_ed25519", "id_dsa", "id_ecdsa",
            "known_hosts", "authorized_keys",
            ".gitconfig", ".npmrc", ".pypirc");

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "class", "jar", "war", "ear", "so", "dll", "dylib",
            "exe", "bin", "obj", "o", "a", "lib",
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "svg",
            "pdf", "zip", "tar", "gz", "bz2", "xz", "7z",
            "db", "sqlite", "sqlite3", "mdb", "accdb");

    private final CodeWorkspaceProperties properties;

    public CodeRepositoryScanner(CodeWorkspaceProperties properties) {
        this.properties = properties;
    }

    /**
     * Searches allowed repositories for files matching the keyword.
     *
     * @param keyword case-insensitive search term
     * @return list of matching files with opaque file IDs
     * @throws IllegalArgumentException if no repositories are configured or keyword is invalid
     */
    public List<CodeSearchResult> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }
        if (keyword.length() > 100) {
            throw new IllegalArgumentException("搜索关键词过长");
        }
        String normalizedKeyword = keyword.strip().toLowerCase(Locale.ROOT);
        List<CodeSearchResult> results = new ArrayList<>();

        for (String repoPath : properties.getAllowedRepositories()) {
            if (repoPath == null || repoPath.isBlank()) {
                continue;
            }
            Path repoRoot = Path.of(repoPath.strip()).toAbsolutePath().normalize();
            validateRepositoryRoot(repoRoot);
            scanDirectory(repoRoot, repoRoot, normalizedKeyword, results);
            if (results.size() >= properties.getMaxSearchResults()) {
                break;
            }
        }

        return List.copyOf(results);
    }

    private void validateRepositoryRoot(Path repoRoot) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(repoRoot,
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isDirectory()) {
                throw new IllegalArgumentException("仓库路径不是目录: " + repoRoot);
            }
            if (Files.isSymbolicLink(repoRoot)) {
                throw new IllegalArgumentException("仓库路径是符号链接，不允许");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法访问仓库目录: " + repoRoot, e);
        }
    }

    private void scanDirectory(
            Path repoRoot,
            Path directory,
            String keyword,
            List<CodeSearchResult> results) {
        int depth = repoRoot.relativize(directory).getNameCount();
        if (depth > properties.getMaxSearchDepth()) {
            return;
        }
        if (results.size() >= properties.getMaxSearchResults()) {
            return;
        }

        try (var stream = Files.list(directory)) {
            for (Path entry : stream.toList()) {
                if (results.size() >= properties.getMaxSearchResults()) {
                    break;
                }
                try {
                    BasicFileAttributes attrs = Files.readAttributes(entry,
                            BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

                    // Reject symlinks
                    if (Files.isSymbolicLink(entry)) {
                        continue;
                    }

                    String fileName = entry.getFileName().toString();

                    if (attrs.isDirectory()) {
                        // Skip sensitive directories
                        if (isSensitiveDirectory(fileName)) {
                            continue;
                        }
                        scanDirectory(repoRoot, entry, keyword, results);
                    } else if (attrs.isRegularFile()) {
                        // Skip binary files
                        if (isBinaryFile(fileName)) {
                            continue;
                        }
                        // Skip sensitive files
                        if (isSensitiveFile(fileName)) {
                            continue;
                        }
                        // Match keyword
                        if (fileName.toLowerCase(Locale.ROOT).contains(keyword)) {
                            String relativePath = repoRoot.relativize(entry).toString();
                            String fileId = "code-" + UUID.randomUUID();
                            results.add(new CodeSearchResult(
                                    relativePath,
                                    fileId,
                                    attrs.size(),
                                    attrs.lastModifiedTime().toInstant()));
                        }
                    }
                } catch (IOException e) {
                    // Skip inaccessible files
                }
            }
        } catch (IOException e) {
            // Skip inaccessible directories
        }
    }

    private boolean isSensitiveDirectory(String name) {
        return SENSITIVE_DIRS.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean isSensitiveFile(String name) {
        return SENSITIVE_FILES.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean isBinaryFile(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return BINARY_EXTENSIONS.contains(ext);
    }
}
