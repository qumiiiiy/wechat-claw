package com.weida.bot.wechatilinkbot.capability;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits for the local file browsing tool.
 *
 * <p>The special allowed-path value {@code *} expands to every mounted file-system root.
 * Traversal is still bounded; sensitive paths and script/key extensions remain excluded by
 * the capability, while searchable file types are governed by {@code allowedExtensions}.</p>
 */
@Validated
@ConfigurationProperties(prefix = "bot.capability.file-browse")
public class FileBrowseProperties {

    private boolean enabled;

    private List<String> allowedPaths = new ArrayList<>();

    @Min(1)
    @Max(2_000_000)
    private int maxEntries = 20_000;

    @Min(1)
    @Max(10)
    private int maxDepth = 8;

    @Min(1)
    @Max(500)
    private int maxResults = 50;

    @Min(1)
    @Max(100)
    private int maxKeywordLength = 50;

    @NotNull
    private Duration maxScanDuration = Duration.ofSeconds(60);

    @NotNull
    private Duration selectionExpireAfter = Duration.ofMinutes(10);

    @Min(1)
    @Max(10_000)
    private int maxSelections = 500;

    @Min(1_024)
    @Max(52_428_800)
    private int maxFileBytes = 10 * 1024 * 1024;

    private List<String> allowedExtensions = new ArrayList<>(List.of(
            "txt", "md", "pdf", "doc", "docx", "xls", "xlsx", "csv", "json",
            "ppt", "pptx", "png", "jpg", "jpeg", "gif", "bmp", "webp",
            "zip", "rar", "7z", "mp3", "mp4", "avi", "mov", "mkv",
            "exe", "msi", "apk"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedPaths() {
        return allowedPaths;
    }

    public void setAllowedPaths(List<String> allowedPaths) {
        this.allowedPaths = allowedPaths;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public int getMaxKeywordLength() {
        return maxKeywordLength;
    }

    public void setMaxKeywordLength(int maxKeywordLength) {
        this.maxKeywordLength = maxKeywordLength;
    }

    public Duration getMaxScanDuration() {
        return maxScanDuration;
    }

    public void setMaxScanDuration(Duration maxScanDuration) {
        this.maxScanDuration = requirePositive(maxScanDuration, "maxScanDuration");
    }

    public Duration getSelectionExpireAfter() {
        return selectionExpireAfter;
    }

    public void setSelectionExpireAfter(Duration selectionExpireAfter) {
        this.selectionExpireAfter = requirePositive(selectionExpireAfter, "selectionExpireAfter");
    }

    public int getMaxSelections() {
        return maxSelections;
    }

    public void setMaxSelections(int maxSelections) {
        this.maxSelections = maxSelections;
    }

    public int getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(int maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public List<String> getAllowedExtensions() {
        return List.copyOf(allowedExtensions);
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions == null
                ? new ArrayList<>()
                : new ArrayList<>(allowedExtensions);
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " 必须大于 0");
        }
        return value;
    }
}
