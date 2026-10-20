package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Searches and selects regular files inside configured roots. */
@Component
public class FileBrowseCapability {

    private static final String ALL_PATHS_MARKER = "*";
    private static final Set<String> SENSITIVE_COMPONENTS = Set.of(
            ".git", ".ssh", ".gnupg", "credentials", "credential",
            "secrets", "secret", "cookies", "cookie", "login data",
            "user data", "browser data");
    private static final Set<String> SENSITIVE_FILE_NAMES = Set.of(
            ".env", "id_rsa", "id_ed25519", "known_hosts", "authorized_keys");
    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            "dll", "com", "scr", "bat", "cmd", "ps1",
            "sh", "bash", "zsh", "fish", "key", "pem", "pfx", "p12");

    private final FileBrowseProperties properties;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final LinkedHashMap<String, SelectedFile> selectedFiles = new LinkedHashMap<>();

    @Autowired
    public FileBrowseCapability(FileBrowseProperties properties) {
        this(properties, Clock.systemUTC(), System::nanoTime);
    }

    public FileBrowseCapability(
            FileBrowseProperties properties,
            Clock clock,
            LongSupplier nanoTime) {
        this.properties = properties;
        this.clock = clock;
        this.nanoTime = nanoTime;
    }

    public boolean available() {
        return properties.isEnabled() && !properties.getAllowedPaths().isEmpty();
    }

    public String availabilityReason() {
        if (!properties.isEnabled()) {
            return "disabled";
        }
        return properties.getAllowedPaths().isEmpty()
                ? "allowed_paths_missing"
                : "available";
    }

    /** Returns bounded metadata and expiring opaque IDs; never returns absolute paths. */
    public List<FileSearchResult> search(String keyword) {
        return search(keyword, "local-call");
    }

    /** Returns metadata and binds every opaque ID to one trusted conversation. */
    public List<FileSearchResult> search(String keyword, String conversationId) {
        requireAvailable();
        String safeKeyword = requireKeyword(keyword).toLowerCase(Locale.ROOT);
        String safeConversationId = requireText(conversationId, "会话 ID 不能为空");
        List<Path> roots = resolveAllowedRoots();
        List<FileSearchResult> results = new ArrayList<>();
        Set<Path> seenFiles = new HashSet<>();
        for (Path root : roots) {
            if (results.size() >= properties.getMaxResults()) {
                break;
            }
            ScanBudget rootBudget = new ScanBudget(scanDeadline(), properties.getMaxEntries());
            scanRoot(root, safeKeyword, safeConversationId, rootBudget, results, seenFiles);
        }
        return List.copyOf(results);
    }

    /** Revalidates an opaque ID and returns a bounded file ready for channel delivery. */
    public GeneratedMedia select(String fileId) {
        return select(fileId, "local-call");
    }

    /** Revalidates an opaque ID for the same trusted conversation that created it. */
    public GeneratedMedia select(String fileId, String conversationId) {
        requireAvailable();
        String safeId = requireText(fileId, "文件 ID 不能为空");
        String safeConversationId = requireText(conversationId, "会话 ID 不能为空");
        SelectedFile selected = selectedFile(safeId);
        if (!selected.conversationId().equals(safeConversationId)) {
            throw new IllegalArgumentException("文件 ID 无效或不属于当前会话");
        }
        if (selected.expiresAt().isBefore(clock.instant())) {
            forget(safeId);
            throw new IllegalArgumentException("文件 ID 已过期，请重新搜索");
        }
        if (!resolveAllowedRoots().contains(selected.root())) {
            forget(safeId);
            throw new IllegalArgumentException("文件已不在允许目录中，请重新搜索");
        }
        Path file = selected.realPath();
        BasicFileAttributes attributes = readSafeAttributes(file);
        Path currentRealPath = realPath(file);
        if (!currentRealPath.startsWith(selected.root())
                || !currentRealPath.equals(selected.realPath())
                || !sameIdentity(selected, attributes)) {
            forget(safeId);
            throw new IllegalArgumentException("文件在搜索后已发生变化，请重新搜索");
        }
        if (attributes.size() > properties.getMaxFileBytes()) {
            throw new IllegalArgumentException("文件超过允许发送大小");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new IllegalArgumentException("文件读取失败，请重新搜索", exception);
        }
        if (bytes.length != attributes.size()) {
            forget(safeId);
            throw new IllegalArgumentException("文件在读取期间发生变化，请重新搜索");
        }
        BasicFileAttributes afterRead = readSafeAttributes(file);
        if (!sameIdentity(selected, afterRead) || !realPath(file).equals(selected.realPath())) {
            forget(safeId);
            throw new IllegalArgumentException("文件在读取期间发生变化，请重新搜索");
        }
        return new GeneratedMedia(
                GeneratedMediaKind.FILE,
                bytes,
                file.getFileName().toString(),
                0L,
                "");
    }

    private void scanRoot(
            Path root,
            String keyword,
            String conversationId,
            ScanBudget budget,
            List<FileSearchResult> results,
            Set<Path> seenFiles) {
        try {
            Files.walkFileTree(root, Set.of(), properties.getMaxDepth(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (budget.exhausted(nanoTime.getAsLong())
                            || results.size() >= properties.getMaxResults()) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (isUnsafeNode(directory, attributes)
                            || isSensitivePath(root, directory)
                            || !directory.startsWith(root)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (!budget.consume(nanoTime.getAsLong())) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (isSafeCandidate(root, file, attributes)
                            && file.getFileName().toString().toLowerCase(Locale.ROOT).contains(keyword)
                            && seenFiles.add(file.toAbsolutePath().normalize())) {
                        results.add(register(root, file, conversationId, attributes));
                    }
                    return results.size() >= properties.getMaxResults()
                            ? FileVisitResult.TERMINATE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("允许目录扫描失败", exception);
        }
    }

    private boolean isSafeCandidate(Path root, Path file, BasicFileAttributes attributes) {
        if (isUnsafeNode(file, attributes)
                || !attributes.isRegularFile()
                || attributes.size() > properties.getMaxFileBytes()
                || isSensitivePath(root, file)) {
            return false;
        }
        return file.startsWith(root) && allowedExtension(file);
    }

    private boolean isUnsafeNode(Path path, BasicFileAttributes attributes) {
        return attributes.isSymbolicLink() || attributes.isOther();
    }

    private boolean isSensitivePath(Path root, Path path) {
        for (Path component : root.relativize(path)) {
            String value = component.toString().toLowerCase(Locale.ROOT);
            if (SENSITIVE_COMPONENTS.contains(value)
                    || SENSITIVE_FILE_NAMES.contains(value)
                    || value.contains("token")
                    || value.contains("credential")
                    || value.contains("secret")) {
                return true;
            }
        }
        String extension = extension(path);
        return FORBIDDEN_EXTENSIONS.contains(extension);
    }

    private boolean allowedExtension(Path path) {
        Set<String> allowed = new HashSet<>();
        for (String extension : properties.getAllowedExtensions()) {
            if (extension != null && !extension.isBlank()) {
                allowed.add(extension.strip().toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""));
            }
        }
        return !allowed.isEmpty() && allowed.contains(extension(path));
    }

    private FileSearchResult register(
            Path root,
            Path file,
            String conversationId,
            BasicFileAttributes attributes) {
        Path real = realPath(file);
        String fileId = "file-" + UUID.randomUUID();
        SelectedFile selected = new SelectedFile(
                conversationId,
                root,
                real,
                identity(attributes),
                attributes.size(),
                attributes.lastModifiedTime().toInstant(),
                clock.instant().plus(properties.getSelectionExpireAfter()));
        synchronized (selectedFiles) {
            removeExpired();
            while (selectedFiles.size() >= properties.getMaxSelections()) {
                Iterator<String> iterator = selectedFiles.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
            selectedFiles.put(fileId, selected);
        }
        return new FileSearchResult(
                root.relativize(real).toString(),
                fileId,
                attributes.size(),
                attributes.lastModifiedTime().toInstant());
    }

    List<Path> resolveAllowedRoots() {
        if (properties.getAllowedPaths().stream().anyMatch(this::isAllPathsMarker)) {
            return resolveAllFileSystemRoots();
        }
        List<Path> roots = new ArrayList<>();
        for (String configuredPath : properties.getAllowedPaths()) {
            if (configuredPath == null || configuredPath.isBlank()) {
                continue;
            }
            Path path = Path.of(configuredPath.strip()).toAbsolutePath().normalize();
            BasicFileAttributes attributes = readSafeAttributes(path);
            if (!attributes.isDirectory() || isUnsafeNode(path, attributes)) {
                throw new IllegalArgumentException("配置的允许目录无效");
            }
            roots.add(realPath(path));
        }
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("没有可用的允许目录");
        }
        return List.copyOf(roots);
    }

    private List<Path> resolveAllFileSystemRoots() {
        // The working directory is a likely application-owned location (for example,
        // downloads/wechat) and must be visited before a broad mounted-root walk.
        List<Path> roots = new ArrayList<>();
        for (Path fileSystemRoot : FileSystems.getDefault().getRootDirectories()) {
            try {
                Path root = fileSystemRoot.toAbsolutePath().normalize();
                BasicFileAttributes attributes = readSafeAttributes(root);
                if (attributes.isDirectory() && !isUnsafeNode(root, attributes)) {
                    roots.add(realPath(root));
                }
            } catch (IllegalArgumentException | SecurityException ignored) {
                // A disconnected or inaccessible mounted root must not hide the other roots.
            }
        }
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("没有可用的文件系统根目录");
        }
        Path workingDirectory = currentWorkingDirectory();
        Path workingRoot = currentFileSystemRoot(workingDirectory);
        if (workingDirectory != null) {
            roots.add(workingDirectory);
        }
        return roots.stream()
                .distinct()
                .sorted(Comparator.comparingInt(root -> {
                    if (root.equals(workingDirectory)) {
                        return 0;
                    }
                    return root.equals(workingRoot) ? 1 : 2;
                }))
                .toList();
    }

    private long scanDeadline() {
        return nanoTime.getAsLong() + properties.getMaxScanDuration().toNanos();
    }

    private Path currentFileSystemRoot(Path workingDirectory) {
        if (workingDirectory != null) {
            return workingDirectory.getRoot();
        }
        try {
            return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().getRoot();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Path currentWorkingDirectory() {
        try {
            Path workingDirectory = Path.of(System.getProperty("user.dir"))
                    .toAbsolutePath()
                    .normalize();
            BasicFileAttributes attributes = readSafeAttributes(workingDirectory);
            return attributes.isDirectory() && !isUnsafeNode(workingDirectory, attributes)
                    ? realPath(workingDirectory)
                    : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean isAllPathsMarker(String configuredPath) {
        return configuredPath != null && ALL_PATHS_MARKER.equals(configuredPath.strip());
    }

    private BasicFileAttributes readSafeAttributes(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() && !attributes.isDirectory()) {
                throw new IllegalArgumentException("目标不是普通文件或目录");
            }
            return attributes;
        } catch (IOException exception) {
            throw new IllegalArgumentException("文件或目录不可访问", exception);
        }
    }

    private Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException("文件或目录不可访问", exception);
        }
    }

    private SelectedFile selectedFile(String fileId) {
        synchronized (selectedFiles) {
            removeExpired();
            SelectedFile selected = selectedFiles.get(fileId);
            if (selected == null) {
                throw new IllegalArgumentException("文件 ID 无效或已过期，请重新搜索");
            }
            return selected;
        }
    }

    private void forget(String fileId) {
        synchronized (selectedFiles) {
            selectedFiles.remove(fileId);
        }
    }

    private void removeExpired() {
        Instant now = clock.instant();
        selectedFiles.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private boolean sameIdentity(SelectedFile selected, BasicFileAttributes attributes) {
        return selected.sizeBytes() == attributes.size()
                && selected.lastModified().equals(attributes.lastModifiedTime().toInstant())
                && selected.fileKey().equals(identity(attributes));
    }

    private static String identity(BasicFileAttributes attributes) {
        return attributes.fileKey() == null ? "no-file-key" : attributes.fileKey().toString();
    }

    private static String extension(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return "";
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1
                ? ""
                : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void requireAvailable() {
        if (!available()) {
            throw new IllegalArgumentException("文件搜索功能未启用或未配置允许目录");
        }
    }

    private String requireKeyword(String keyword) {
        String value = requireText(keyword, "搜索关键词不能为空");
        if (value.length() > properties.getMaxKeywordLength()) {
            throw new IllegalArgumentException(
                    "搜索关键词超过长度限制：" + properties.getMaxKeywordLength());
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    public record FileSearchResult(
            String relativePath,
            String fileId,
            long sizeBytes,
            Instant lastModifiedTime) {
    }

    /**
     * Structured search with query parsing and sorting.
     * Returns results with natural indices for disambiguation.
     */
    public List<StructuredFileResult> searchStructured(FileBrowseQuery query, String conversationId) {
        requireAvailable();
        String safeConversationId = requireText(conversationId, "会话 ID 不能为空");
        List<Path> roots = filterRootsByHint(resolveAllowedRoots(), query.directoryHint());
        List<RawSearchHit> rawHits = new ArrayList<>();
        Set<Path> seenFiles = new HashSet<>();
        for (Path root : roots) {
            ScanBudget rootBudget = new ScanBudget(scanDeadline(), properties.getMaxEntries());
            scanRootForStructured(
                    root,
                    query,
                    safeConversationId,
                    rootBudget,
                    rawHits,
                    seenFiles);
        }
        List<RawSearchHit> sorted = sortHits(rawHits, query);
        List<StructuredFileResult> results = new ArrayList<>();
        for (int i = 0; i < sorted.size() && i < properties.getMaxResults(); i++) {
            RawSearchHit hit = sorted.get(i);
            String fileId = registerStructured(rootForHit(hit), hit.file(), safeConversationId, hit.attributes());
            results.add(new StructuredFileResult(
                    i + 1,
                    hit.file().getFileName().toString(),
                    relativeDirectory(rootForHit(hit), hit.file()),
                    hit.attributes().size(),
                    humanReadableSize(hit.attributes().size()),
                    hit.attributes().lastModifiedTime().toInstant(),
                    fileId));
        }
        return List.copyOf(results);
    }

    /** Lists bounded directory metadata without creating selectable file references. */
    public List<StructuredDirectoryResult> searchDirectoriesStructured(
            FileBrowseQuery query,
            String conversationId) {
        requireAvailable();
        requireText(conversationId, "会话 ID 不能为空");
        List<RawDirectoryHit> rawHits = new ArrayList<>();
        Set<Path> seenDirectories = new HashSet<>();
        for (Path root : filterRootsByHint(resolveAllowedRoots(), query.directoryHint())) {
            ScanBudget rootBudget = new ScanBudget(scanDeadline(), properties.getMaxEntries());
            scanRootForDirectories(root, query, rootBudget, rawHits, seenDirectories);
        }
        List<RawDirectoryHit> sorted = sortDirectoryHits(rawHits, query);
        List<StructuredDirectoryResult> results = new ArrayList<>();
        for (int index = 0; index < sorted.size() && index < properties.getMaxResults(); index++) {
            RawDirectoryHit hit = sorted.get(index);
            Path directory = realPath(hit.directory());
            String relativePath = rootRelativePath(hit.root(), directory);
            if (!relativePath.isBlank()) {
                results.add(new StructuredDirectoryResult(
                        results.size() + 1,
                        directory.getFileName().toString(),
                        relativePath));
            }
        }
        return List.copyOf(results);
    }

    /**
     * Selects a file by natural index (1-based) from the most recent search.
     */
    public GeneratedMedia selectByNaturalIndex(int naturalIndex, String conversationId) {
        requireAvailable();
        String safeConversationId = requireText(conversationId, "会话 ID 不能为空");
        if (naturalIndex < 1) {
            throw new IllegalArgumentException("序号必须 >= 1");
        }
        String fileId = findFileIdByIndex(safeConversationId, naturalIndex);
        return select(fileId, safeConversationId);
    }

    private void scanRootForStructured(
            Path root,
            FileBrowseQuery query,
            String conversationId,
            ScanBudget budget,
            List<RawSearchHit> results,
            Set<Path> seenFiles) {
        try {
            Files.walkFileTree(root, Set.of(), properties.getMaxDepth(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (budget.exhausted(nanoTime.getAsLong())) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (isUnsafeNode(directory, attributes)
                            || isSensitivePath(root, directory)
                            || !realPath(directory).startsWith(root)
                            || !matchesDirectoryHint(root, directory, query)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (!budget.consume(nanoTime.getAsLong())) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (isSafeCandidate(root, file, attributes)
                            && matchesStructuredQuery(file, attributes, query)
                            && seenFiles.add(realPath(file))) {
                        results.add(new RawSearchHit(root, file, attributes));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("允许目录扫描失败", exception);
        }
    }

    private void scanRootForDirectories(
            Path root,
            FileBrowseQuery query,
            ScanBudget budget,
            List<RawDirectoryHit> results,
            Set<Path> seenDirectories) {
        try {
            Files.walkFileTree(root, Set.of(), properties.getMaxDepth(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(
                        Path directory,
                        BasicFileAttributes attributes) {
                    if (!budget.consume(nanoTime.getAsLong())) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (isUnsafeNode(directory, attributes)
                            || isSensitivePath(root, directory)
                            || !realPath(directory).startsWith(root)
                            || !matchesDirectoryHint(root, directory, query)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!directory.equals(root)
                            && matchesDirectoryQuery(directory, attributes, query)
                            && seenDirectories.add(realPath(directory))) {
                        results.add(new RawDirectoryHit(root, directory, attributes));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("允许目录扫描失败", exception);
        }
    }

    private boolean matchesDirectoryHint(Path root, Path directory, FileBrowseQuery query) {
        if (!query.hasDirectoryHint()) {
            return true;
        }
        String relativePath = normalizeDirectoryPath(root.relativize(directory).toString());
        String absolutePath = normalizeDirectoryPath(directory.toString());
        String hint = resolveHintDirectory(query.directoryHint());
        if (hint.contains(":")) {
            return isSameOrAncestorPath(absolutePath, hint)
                    || isSameOrDescendantOfPath(absolutePath, hint);
        }
        return relativePath.isEmpty() || relativePath.contains(hint);
    }

    private String resolveHintDirectory(String rawHint) {
        String hint = normalizeDirectoryPath(rawHint);
        if (!hint.contains(":")) {
            return hint;
        }
        if (hint.matches("[a-z]:/")) {
            return hint;
        }
        try {
            Path hintPath = Path.of(rawHint).toRealPath();
            return normalizeDirectoryPath(hintPath.toString());
        } catch (Exception ignored) {
            int slash = hint.indexOf('/');
            return slash > 0 ? hint.substring(0, slash + 1) : hint;
        }
    }

    private boolean isSameOrAncestorPath(String candidate, String hint) {
        return candidate.equals(hint) || hint.startsWith(candidate + "/");
    }

    private boolean isSameOrDescendantOfPath(String candidate, String hint) {
        if (candidate.equals(hint)) {
            return true;
        }
        String prefix = hint.endsWith("/") ? hint : hint + "/";
        return candidate.startsWith(prefix);
    }

    private List<Path> filterRootsByHint(List<Path> roots, String directoryHint) {
        if (directoryHint == null || directoryHint.isBlank()) {
            return roots;
        }
        String hint = normalizeDirectoryPath(directoryHint);
        if (!hint.contains(":")) {
            return roots;
        }
        String effectiveHint = resolveHintDirectory(directoryHint);
        List<Path> filtered = new ArrayList<>();
        for (Path root : roots) {
            String rootPath = normalizeDirectoryPath(root.toString());
            if (isSameOrAncestorPath(rootPath, effectiveHint)
                    || isSameOrAncestorPath(effectiveHint, rootPath)
                    || isSameOrDescendantOfPath(rootPath, effectiveHint)) {
                filtered.add(root);
            }
        }
        return filtered.isEmpty() ? roots : filtered;
    }

    private String normalizeDirectoryPath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip()
                .replace('\\', '/')
                .replaceAll("/+", "/")
                .toLowerCase(Locale.ROOT);
        if (normalized.matches("[a-z]:$")) {
            normalized = normalized + "/";
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            if (normalized.length() == 3 && normalized.charAt(1) == ':') {
                break;
            }
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean matchesStructuredQuery(Path file, BasicFileAttributes attributes, FileBrowseQuery query) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (query.hasNameFragments() && !matchesAnyFragment(fileName, query.nameFragments())) {
            return false;
        }
        if (query.hasFileTypes() && !matchesFileType(file, query.fileTypes())) {
            return false;
        }
        if (query.hasModifiedAfter() && attributes.lastModifiedTime().toInstant().isBefore(query.modifiedAfter())) {
            return false;
        }
        return true;
    }

    private boolean matchesDirectoryQuery(
            Path directory,
            BasicFileAttributes attributes,
            FileBrowseQuery query) {
        String directoryName = directory.getFileName().toString().toLowerCase(Locale.ROOT);
        if (query.hasNameFragments()
                && !matchesAnyFragment(directoryName, query.nameFragments())) {
            return false;
        }
        return !query.hasModifiedAfter()
                || !attributes.lastModifiedTime().toInstant().isBefore(query.modifiedAfter());
    }

    private static boolean matchesAnyFragment(String candidate, List<String> nameFragments) {
        for (String fragment : nameFragments) {
            if (candidate.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesFileType(Path file, List<String> fileTypes) {
        String ext = extension(file);
        for (String type : fileTypes) {
            if (ext.equals(type.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<RawSearchHit> sortHits(List<RawSearchHit> hits, FileBrowseQuery query) {
        List<RawSearchHit> sorted = new ArrayList<>(hits);
        switch (query.sortCriteria()) {
            case RECENT_FIRST:
                sorted.sort((a, b) -> {
                    int relevance = Integer.compare(
                            relevance(b.file(), query),
                            relevance(a.file(), query));
                    return relevance != 0
                            ? relevance
                            : b.attributes().lastModifiedTime()
                                    .compareTo(a.attributes().lastModifiedTime());
                });
                break;
            case NAME_ASC:
                sorted.sort((a, b) -> {
                    int relevance = Integer.compare(
                            relevance(b.file(), query),
                            relevance(a.file(), query));
                    return relevance != 0
                            ? relevance
                            : a.file().getFileName().toString()
                                    .compareToIgnoreCase(b.file().getFileName().toString());
                });
                break;
            case SIZE_DESC:
                sorted.sort((a, b) -> {
                    int relevance = Integer.compare(
                            relevance(b.file(), query),
                            relevance(a.file(), query));
                    return relevance != 0
                            ? relevance
                            : Long.compare(b.attributes().size(), a.attributes().size());
                });
                break;
        }
        return sorted;
    }

    private static int relevance(Path file, FileBrowseQuery query) {
        if (!query.hasNameFragments()) {
            return 1;
        }
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int matchedFragments = 0;
        for (String fragment : query.nameFragments()) {
            if (fileName.contains(fragment)) {
                matchedFragments++;
            }
        }
        return matchedFragments == 0 ? 1 : matchedFragments;
    }

    private List<RawDirectoryHit> sortDirectoryHits(
            List<RawDirectoryHit> hits,
            FileBrowseQuery query) {
        List<RawDirectoryHit> sorted = new ArrayList<>(hits);
        switch (query.sortCriteria()) {
            case RECENT_FIRST:
                sorted.sort((a, b) -> {
                    int relevance = Integer.compare(
                            directoryRelevance(b.directory(), query),
                            directoryRelevance(a.directory(), query));
                    return relevance != 0
                            ? relevance
                            : b.attributes().lastModifiedTime()
                                    .compareTo(a.attributes().lastModifiedTime());
                });
                break;
            case SIZE_DESC:
                sorted.sort((a, b) -> {
                    int relevance = Integer.compare(
                            directoryRelevance(b.directory(), query),
                            directoryRelevance(a.directory(), query));
                    return relevance != 0
                            ? relevance
                            : Long.compare(b.attributes().size(), a.attributes().size());
                });
                break;
            case NAME_ASC:
                sorted.sort((a, b) -> {
                    int relevance = Integer.compare(
                            directoryRelevance(b.directory(), query),
                            directoryRelevance(a.directory(), query));
                    return relevance != 0
                            ? relevance
                            : a.directory().toString()
                                    .compareToIgnoreCase(b.directory().toString());
                });
                break;
        }
        return sorted;
    }

    private static int directoryRelevance(Path directory, FileBrowseQuery query) {
        if (!query.hasNameFragments()) {
            return 1;
        }
        String directoryName = directory.getFileName().toString().toLowerCase(Locale.ROOT);
        int matchedFragments = 0;
        for (String fragment : query.nameFragments()) {
            if (directoryName.contains(fragment)) {
                matchedFragments++;
            }
        }
        return matchedFragments == 0 ? 1 : matchedFragments;
    }

    private Path rootForHit(RawSearchHit hit) {
        return hit.root();
    }

    private String relativeDirectory(Path root, Path file) {
        Path relative = root.relativize(file.getParent());
        return relative.toString().isEmpty() ? "" : relative.toString();
    }

    private String rootRelativePath(Path root, Path path) {
        Path relative = root.relativize(path);
        return relative.toString().isEmpty() ? "" : relative.toString();
    }

    private String humanReadableSize(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        } else if (sizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeBytes / 1024.0);
        } else if (sizeBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private String registerStructured(Path root, Path file, String conversationId, BasicFileAttributes attributes) {
        Path real = realPath(file);
        String fileId = "file-" + UUID.randomUUID();
        SelectedFile selected = new SelectedFile(
                conversationId,
                root,
                real,
                identity(attributes),
                attributes.size(),
                attributes.lastModifiedTime().toInstant(),
                clock.instant().plus(properties.getSelectionExpireAfter()));
        synchronized (selectedFiles) {
            removeExpired();
            while (selectedFiles.size() >= properties.getMaxSelections()) {
                Iterator<String> iterator = selectedFiles.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
            selectedFiles.put(fileId, selected);
        }
        return fileId;
    }

    private String findFileIdByIndex(String conversationId, int naturalIndex) {
        synchronized (selectedFiles) {
            removeExpired();
            int index = 0;
            for (Map.Entry<String, SelectedFile> entry : selectedFiles.entrySet()) {
                if (entry.getValue().conversationId().equals(conversationId)) {
                    index++;
                    if (index == naturalIndex) {
                        return entry.getKey();
                    }
                }
            }
        }
        throw new IllegalArgumentException("序号 " + naturalIndex + " 对应的文件不存在或已过期");
    }

    private record RawSearchHit(Path root, Path file, BasicFileAttributes attributes) {
    }

    private record RawDirectoryHit(
            Path root,
            Path directory,
            BasicFileAttributes attributes) {
    }

    private record SelectedFile(
            String conversationId,
            Path root,
            Path realPath,
            String fileKey,
            long sizeBytes,
            Instant lastModified,
            Instant expiresAt) {
    }

    private static final class ScanBudget {

        private final long deadlineNanos;
        private int remainingEntries;

        private ScanBudget(long deadlineNanos, int maxEntries) {
            this.deadlineNanos = deadlineNanos;
            this.remainingEntries = maxEntries;
        }

        private boolean consume(long nowNanos) {
            if (exhausted(nowNanos)) {
                return false;
            }
            remainingEntries--;
            return true;
        }

        private boolean exhausted(long nowNanos) {
            return remainingEntries <= 0 || nowNanos >= deadlineNanos;
        }
    }
}
