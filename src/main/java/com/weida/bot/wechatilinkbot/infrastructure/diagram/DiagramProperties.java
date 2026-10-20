package com.weida.bot.wechatilinkbot.infrastructure.diagram;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated configuration for the PlantUML diagram renderer.
 *
 * <p>GPL compliance note: PlantUML is GPL-licensed. This configuration only
 * binds an external CLI invocation path; no PlantUML classes are linked or
 * redistributed by this module. Operators are responsible for obtaining and
 * licensing PlantUML on the host.</p>
 *
 * @author ryx
 * @since 2026-07-24
 */
@Validated
@ConfigurationProperties(prefix = "bot.infrastructure.diagram")
public class DiagramProperties {

    private boolean enabled = false;

    @NotBlank
    private String executable = "plantuml";

    @NotNull
    private Duration timeout = Duration.ofSeconds(60);

    @Min(1024)
    private int maxSourceCharacters = 8_000;

    @Min(1024)
    private int maxOutputBytes = 7_340_032;

    /**
     * Total attempts per render request, including the first try. Values
     * below 1 are coerced to 1. Retries only fire on
     * {@code DIAGRAM_TIMEOUT}; validation or process-failure errors fail
     * immediately because retrying them would not change the outcome.
     */
    @Min(1)
    @Max(5)
    private int retryAttempts = 3;

    /**
     * Delay inserted between retry attempts. Doubles on each subsequent
     * retry up to {@code retryDelay * 4} so a long hang in the underlying
     * PlantUML JVM gets progressively more breathing room.
     */
    @NotNull
    private Duration retryDelay = Duration.ofMillis(500);

    @NotEmpty
    private List<String> allowedDiagramTypes = List.of(
            "sequence",
            "class",
            "flowchart",
            "state",
            "activity",
            "usecase",
            "component",
            "deployment",
            "object",
            "timing",
            "erd",
            "gantt",
            "mindmap",
            "wbs",
            "salt");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExecutable() {
        return executable;
    }

    public void setExecutable(String executable) {
        this.executable = executable == null ? "" : executable.strip();
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxSourceCharacters() {
        return maxSourceCharacters;
    }

    public void setMaxSourceCharacters(int maxSourceCharacters) {
        this.maxSourceCharacters = maxSourceCharacters;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public List<String> getAllowedDiagramTypes() {
        return allowedDiagramTypes == null ? List.of() : List.copyOf(allowedDiagramTypes);
    }

    public void setAllowedDiagramTypes(List<String> allowedDiagramTypes) {
        this.allowedDiagramTypes = allowedDiagramTypes == null
                ? List.of()
                : List.copyOf(allowedDiagramTypes);
    }

    public int getRetryAttempts() {
        return Math.max(1, retryAttempts);
    }

    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public Duration getRetryDelay() {
        return retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()
                ? Duration.ofMillis(500)
                : retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

}
