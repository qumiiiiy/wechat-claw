package com.weida.bot.wechatilinkbot.infrastructure.document;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated document generation configuration.
 *
 * <p>Owns both Word and PDF paths. The Word renderer relies on
 * {@link #getOutputDirectory()} + {@link #getWriteTimeout()} to perform the
 * atomic temp-file + POI re-read flow. PDF rendering continues to produce
 * bytes in memory and does not touch the output directory.</p>
 *
 * @author ryx
 * @since 2026-07-24
 */
@Validated
@ConfigurationProperties(prefix = "bot.infrastructure.document")
public class DocumentGenerationProperties {

    private boolean enabled = true;

    @NotNull
    private Path outputDirectory = Paths.get("generated", "document");

    @Min(1000)
    @Max(500000)
    private int maxContentCharacters = 100000;

    @Min(1000)
    @Max(10000000)
    private int maxContentBytes = 5000000;

    @Min(10)
    @Max(500)
    private int maxFileNameCharacters = 100;

    @Min(1)
    @Max(2000)
    private int maxSections = 200;

    @Min(1)
    @Max(20000)
    private int maxParagraphCharacters = 5000;

    @Min(1)
    @Max(2000)
    private int maxListItems = 200;

    @Min(1)
    @Max(2000)
    private int maxTableRows = 200;

    @Min(1)
    @Max(100)
    private int maxTableColumns = 20;

    @Min(1)
    @Max(20000)
    private int maxTableCellCharacters = 2000;

    @Min(1024)
    @Max(50 * 1024 * 1024)
    private int maxOutputBytes = 10 * 1024 * 1024;

    @NotNull
    private Duration writeTimeout = Duration.ofSeconds(30);

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(10);

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(60);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public int getMaxContentCharacters() {
        return maxContentCharacters;
    }

    public void setMaxContentCharacters(int maxContentCharacters) {
        this.maxContentCharacters = maxContentCharacters;
    }

    public int getMaxContentBytes() {
        return maxContentBytes;
    }

    public void setMaxContentBytes(int maxContentBytes) {
        this.maxContentBytes = maxContentBytes;
    }

    public int getMaxFileNameCharacters() {
        return maxFileNameCharacters;
    }

    public void setMaxFileNameCharacters(int maxFileNameCharacters) {
        this.maxFileNameCharacters = maxFileNameCharacters;
    }

    public int getMaxSections() {
        return maxSections;
    }

    public void setMaxSections(int maxSections) {
        this.maxSections = maxSections;
    }

    public int getMaxParagraphCharacters() {
        return maxParagraphCharacters;
    }

    public void setMaxParagraphCharacters(int maxParagraphCharacters) {
        this.maxParagraphCharacters = maxParagraphCharacters;
    }

    public int getMaxListItems() {
        return maxListItems;
    }

    public void setMaxListItems(int maxListItems) {
        this.maxListItems = maxListItems;
    }

    public int getMaxTableRows() {
        return maxTableRows;
    }

    public void setMaxTableRows(int maxTableRows) {
        this.maxTableRows = maxTableRows;
    }

    public int getMaxTableColumns() {
        return maxTableColumns;
    }

    public void setMaxTableColumns(int maxTableColumns) {
        this.maxTableColumns = maxTableColumns;
    }

    public int getMaxTableCellCharacters() {
        return maxTableCellCharacters;
    }

    public void setMaxTableCellCharacters(int maxTableCellCharacters) {
        this.maxTableCellCharacters = maxTableCellCharacters;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}