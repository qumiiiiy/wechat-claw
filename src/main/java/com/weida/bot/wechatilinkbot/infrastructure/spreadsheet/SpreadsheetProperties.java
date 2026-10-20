package com.weida.bot.wechatilinkbot.infrastructure.spreadsheet;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated configuration for the opt-in XLSX renderer.
 *
 * <p>The default state is <strong>disabled</strong>, matching the rest of
 * the project's opt-in capability conventions. The
 * {@code application.properties} env-var bindings are intentionally left to
 * the integrator (role {@code A}); defaults configured here apply when no
 * overrides are supplied.</p>
 *
 * @author claw-sprint (role C)
 * @since 2026-07-30
 */
@Validated
@ConfigurationProperties(prefix = "bot.infrastructure.spreadsheet")
public class SpreadsheetProperties {

    private boolean enabled = false;

    @NotNull
    private Path outputDirectory = Paths.get("generated", "spreadsheet");

    @Min(1)
    private int maxColumns = 64;

    @Min(1)
    private int maxRows = 10_000;

    @Min(1)
    private int maxCellCharacters = 32_767;

    @Min(1)
    private int maxTitleCharacters = 100;

    @Min(1)
    private int maxFileNameCharacters = 64;

    @Min(1024)
    private int maxOutputBytes = 10 * 1024 * 1024;

    @NotNull
    private Duration writeTimeout = Duration.ofSeconds(30);

    @Min(1024)
    private int maxReadBytes = 10 * 1024 * 1024;

    @Min(1)
    private int maxReadSheets = 8;

    @Min(1)
    private int maxReadColumns = 256;

    @Min(1)
    private int maxReadRows = 50_000;

    @NotBlank
    private String defaultSheetName = "Sheet1";

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

    public int getMaxColumns() {
        return maxColumns;
    }

    public void setMaxColumns(int maxColumns) {
        this.maxColumns = maxColumns;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public int getMaxCellCharacters() {
        return maxCellCharacters;
    }

    public void setMaxCellCharacters(int maxCellCharacters) {
        this.maxCellCharacters = maxCellCharacters;
    }

    public int getMaxTitleCharacters() {
        return maxTitleCharacters;
    }

    public void setMaxTitleCharacters(int maxTitleCharacters) {
        this.maxTitleCharacters = maxTitleCharacters;
    }

    public int getMaxFileNameCharacters() {
        return maxFileNameCharacters;
    }

    public void setMaxFileNameCharacters(int maxFileNameCharacters) {
        this.maxFileNameCharacters = maxFileNameCharacters;
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

    public int getMaxReadBytes() {
        return maxReadBytes;
    }

    public void setMaxReadBytes(int maxReadBytes) {
        this.maxReadBytes = maxReadBytes;
    }

    public int getMaxReadSheets() {
        return maxReadSheets;
    }

    public void setMaxReadSheets(int maxReadSheets) {
        this.maxReadSheets = maxReadSheets;
    }

    public int getMaxReadColumns() {
        return maxReadColumns;
    }

    public void setMaxReadColumns(int maxReadColumns) {
        this.maxReadColumns = maxReadColumns;
    }

    public int getMaxReadRows() {
        return maxReadRows;
    }

    public void setMaxReadRows(int maxReadRows) {
        this.maxReadRows = maxReadRows;
    }

    public String getDefaultSheetName() {
        return defaultSheetName;
    }

    public void setDefaultSheetName(String defaultSheetName) {
        this.defaultSheetName = defaultSheetName;
    }
}
