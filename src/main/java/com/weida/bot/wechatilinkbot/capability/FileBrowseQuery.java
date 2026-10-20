package com.weida.bot.wechatilinkbot.capability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Immutable, bounded query understood by the local file browsing capability. */
public record FileBrowseQuery(
        List<String> nameFragments,
        List<String> fileTypes,
        String directoryHint,
        Instant modifiedAfter,
        FileSortCriteria sortCriteria,
        boolean directoriesOnly) {

    public FileBrowseQuery(
            List<String> nameFragments,
            List<String> fileTypes,
            String directoryHint,
            Instant modifiedAfter,
            FileSortCriteria sortCriteria) {
        this(nameFragments, fileTypes, directoryHint, modifiedAfter, sortCriteria, false);
    }

    public FileBrowseQuery {
        nameFragments = normalizeFragments(nameFragments);
        directoryHint = normalize(directoryHint);
        fileTypes = normalizeTypes(fileTypes);
        sortCriteria = sortCriteria == null ? FileSortCriteria.NAME_ASC : sortCriteria;
    }

    public boolean hasNameFragments() {
        return !nameFragments.isEmpty();
    }

    public boolean hasFileTypes() {
        return !fileTypes.isEmpty();
    }

    public boolean hasDirectoryHint() {
        return directoryHint != null;
    }

    public boolean hasModifiedAfter() {
        return modifiedAfter != null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static List<String> normalizeFragments(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static List<String> normalizeTypes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String extension = value.strip().toLowerCase().replaceFirst("^\\.", "");
            if (!extension.isEmpty() && !normalized.contains(extension)) {
                normalized.add(extension);
            }
        }
        return List.copyOf(normalized);
    }

    public enum FileSortCriteria {
        RECENT_FIRST,
        NAME_ASC,
        SIZE_DESC
    }
}
