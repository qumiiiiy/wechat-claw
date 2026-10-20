package com.weida.bot.wechatilinkbot.infrastructure.pptx;

import jakarta.validation.constraints.Min;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Server-controlled PPTX rendering settings. Mirrors the shape of
 * {@code DocumentGenerationProperties} so that the capability layer can
 * follow the same atomic-write + verify flow without surprising defaults.
 *
 * <p>All fields are intentionally configurable; the renderer enforces
 * them on every {@code render} call so a misconfigured deployment cannot
 * be tricked into producing an oversized deck.</p>
 */
@ConfigurationProperties(prefix = "bot.infrastructure.pptx")
@Validated
public class PptxDocumentProperties {

    /** Master switch. Defaults to {@code false} so existing deployments
     *  remain unchanged until the deck capability is wired into the agent. */
    private boolean enabled = false;

    /** Output directory resolved relative to the working directory. */
    private Path outputDirectory = Paths.get("generated", "pptx");

    /** Max slides per deck. */
    @Min(1)
    private int maxSlides = 64;

    /** Max bullets per bullet slide. */
    @Min(1)
    private int maxBullets = 12;

    /** Max table rows per table slide. */
    @Min(1)
    private int maxTableRows = 32;

    /** Max table columns per table slide. */
    @Min(1)
    private int maxTableColumns = 12;

    /** Max bytes for a single rendered PPTX. */
    @Min(1024)
    private long maxOutputBytes = 10L * 1024 * 1024;

    /** Max characters allowed in any text frame (title, subtitle, etc.). */
    @Min(64)
    private int maxTextCharacters = 4096;

    /** Atomic-write deadline. */
    private Duration writeTimeout = Duration.ofSeconds(30);

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

    public int getMaxSlides() {
        return maxSlides;
    }

    public void setMaxSlides(int maxSlides) {
        this.maxSlides = maxSlides;
    }

    public int getMaxBullets() {
        return maxBullets;
    }

    public void setMaxBullets(int maxBullets) {
        this.maxBullets = maxBullets;
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

    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public int getMaxTextCharacters() {
        return maxTextCharacters;
    }

    public void setMaxTextCharacters(int maxTextCharacters) {
        this.maxTextCharacters = maxTextCharacters;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }
}