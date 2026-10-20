package com.weida.bot.wechatilinkbot.capability;

import java.text.Normalizer;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and normalizes model-produced structured file search conditions.
 *
 * <p>This class performs no natural-language interpretation. It only enforces bounded,
 * deterministic safety rules (length, count, path separators, extension shape) and converts
 * the model's structured request into the immutable {@link FileBrowseQuery} executed by the
 * capability. Semantic understanding, synonyms and query decomposition belong to the model.</p>
 */
public final class FileBrowseQueryCompiler {

    private static final int MAX_FILE_TYPES = 10;
    private static final int MAX_DIRECTORY_HINT_CHARACTERS = 200;
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("[a-z0-9]{1,8}");

    private final Clock clock;
    private final int maxFragmentLength;
    private final int maxFragments;

    public FileBrowseQueryCompiler() {
        this(Clock.systemUTC(), 50, 5);
    }

    public FileBrowseQueryCompiler(FileBrowseProperties properties) {
        this(Clock.systemUTC(), properties.getMaxKeywordLength(), 5);
    }

    FileBrowseQueryCompiler(Clock clock, int maxFragmentLength, int maxFragments) {
        this.clock = clock;
        this.maxFragmentLength = maxFragmentLength;
        this.maxFragments = maxFragments;
    }

    /**
     * Compiles bounded structured conditions into the executable query.
     *
     * @throws IllegalArgumentException when the request has no usable condition or any field
     *                                  violates a safety bound
     */
    public FileBrowseQuery compile(FileBrowseSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("文件搜索条件不能为空");
        }
        List<String> nameFragments = normalizeNameFragments(request.nameFragments());
        List<String> fileTypes = normalizeFileTypes(request.fileTypes());
        String directoryHint = normalizeDirectoryHint(request.directoryHint());
        boolean directoriesOnly = Boolean.TRUE.equals(request.directoriesOnly());
        boolean recentFileReference = Boolean.TRUE.equals(request.recentFileReference());
        if (nameFragments.isEmpty()
                && fileTypes.isEmpty()
                && directoryHint == null
                && !directoriesOnly
                && !recentFileReference) {
            throw new IllegalArgumentException("文件搜索条件不能为空");
        }
        FileBrowseQuery.FileSortCriteria sortCriteria = normalizeSortCriteria(
                request.sortCriteria());
        return new FileBrowseQuery(
                nameFragments,
                fileTypes,
                directoryHint,
                null,
                sortCriteria,
                directoriesOnly);
    }

    private List<String> normalizeNameFragments(List<String> rawFragments) {
        if (rawFragments == null || rawFragments.isEmpty()) {
            return List.of();
        }
        if (rawFragments.size() > maxFragments) {
            throw new IllegalArgumentException("文件名关键词最多 " + maxFragments + " 个");
        }
        Set<String> normalizedSet = new LinkedHashSet<>();
        for (String rawFragment : rawFragments) {
            String fragment = normalizeText(rawFragment);
            if (fragment.isBlank()) {
                continue;
            }
            if (fragment.length() > maxFragmentLength) {
                throw new IllegalArgumentException(
                        "文件名关键词超过长度限制：" + maxFragmentLength);
            }
            if (fragment.contains("/") || fragment.contains("\\") || fragment.contains("..")) {
                throw new IllegalArgumentException("文件名关键词不能包含路径分隔符");
            }
            normalizedSet.add(fragment.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalizedSet);
    }

    private static List<String> normalizeFileTypes(List<String> rawFileTypes) {
        if (rawFileTypes == null || rawFileTypes.isEmpty()) {
            return List.of();
        }
        if (rawFileTypes.size() > MAX_FILE_TYPES) {
            throw new IllegalArgumentException("文件类型最多 " + MAX_FILE_TYPES + " 个");
        }
        Set<String> normalizedSet = new LinkedHashSet<>();
        for (String rawType : rawFileTypes) {
            String type = normalizeText(rawType).toLowerCase(Locale.ROOT);
            while (type.startsWith(".")) {
                type = type.substring(1);
            }
            if (type.isBlank() || !EXTENSION_PATTERN.matcher(type).matches()) {
                throw new IllegalArgumentException("文件类型必须是合法扩展名：" + rawType);
            }
            normalizedSet.add(type);
        }
        return List.copyOf(normalizedSet);
    }

    private static String normalizeDirectoryHint(String rawHint) {
        if (rawHint == null || rawHint.isBlank()) {
            return null;
        }
        String hint = normalizeText(rawHint);
        if (hint.length() > MAX_DIRECTORY_HINT_CHARACTERS) {
            throw new IllegalArgumentException("目录条件超过长度限制：" + MAX_DIRECTORY_HINT_CHARACTERS);
        }
        String lower = hint.toLowerCase(Locale.ROOT);
        if (lower.matches("[a-z]盘.*")) {
            hint = lower.charAt(0) + ":/" + hint.substring(2);
        }
        String path = hint
                .replace('\\', '/')
                .replaceAll("/+", "/")
                .replaceAll("/+$", "")
                .strip();
        if (path.toLowerCase(Locale.ROOT).matches("[a-z]:$")) {
            path = path.toLowerCase(Locale.ROOT) + "/";
        }
        if (path.isEmpty()) {
            return null;
        }
        for (String component : path.split("/")) {
            if ("..".equals(component)) {
                throw new IllegalArgumentException("目录条件不能包含上级路径");
            }
        }
        return path;
    }

    private static FileBrowseQuery.FileSortCriteria normalizeSortCriteria(String rawCriteria) {
        if (rawCriteria == null || rawCriteria.isBlank()) {
            return FileBrowseQuery.FileSortCriteria.NAME_ASC;
        }
        String normalized = rawCriteria.strip().toUpperCase(Locale.ROOT);
        try {
            return FileBrowseQuery.FileSortCriteria.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "排序条件必须是 recent_first、name_asc 或 size_desc");
        }
    }

    private static String normalizeText(String value) {
        return value == null
                ? ""
                : Normalizer.normalize(value.strip(), Normalizer.Form.NFKC);
    }
}
