package com.weida.bot.wechatilinkbot.infrastructure.code;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the secure code workspace capability.
 *
 * <p>All paths are validated at startup; search, read, diff and patch
 * operations are bounded by the limits below.</p>
 */
@Validated
@ConfigurationProperties(prefix = "bot.infrastructure.code-workspace")
public class CodeWorkspaceProperties {

    private boolean enabled = false;

    @NotNull
    private List<String> allowedRepositories = new ArrayList<>();

    @Min(1)
    @Max(100)
    private int maxSearchResults = 20;

    @Min(1)
    @Max(10)
    private int maxSearchDepth = 5;

    @Min(1)
    @Max(500)
    private int maxReadLines = 100;

    @Min(1)
    @Max(50000)
    private int maxReadBytes = 10000;

    @Min(1)
    @Max(10000)
    private int maxDiffLines = 500;

    @Min(1)
    @Max(100000)
    private int maxPatchBytes = 50000;

    @Min(1)
    @Max(100)
    private int maxPatchHunks = 20;

    @Min(1)
    @Max(100)
    private int maxTestTimeoutSeconds = 60;

    @NotNull
    private Duration patchConfirmTtl = Duration.ofMinutes(5);

    @Min(1)
    @Max(1000)
    private int maxPendingConfirmations = 50;

    @NotNull
    private String testCommand = "";

    @NotNull
    private Duration testTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedRepositories() {
        return allowedRepositories;
    }

    public void setAllowedRepositories(List<String> allowedRepositories) {
        this.allowedRepositories = allowedRepositories;
    }

    public int getMaxSearchResults() {
        return maxSearchResults;
    }

    public void setMaxSearchResults(int maxSearchResults) {
        this.maxSearchResults = maxSearchResults;
    }

    public int getMaxSearchDepth() {
        return maxSearchDepth;
    }

    public void setMaxSearchDepth(int maxSearchDepth) {
        this.maxSearchDepth = maxSearchDepth;
    }

    public int getMaxReadLines() {
        return maxReadLines;
    }

    public void setMaxReadLines(int maxReadLines) {
        this.maxReadLines = maxReadLines;
    }

    public int getMaxReadBytes() {
        return maxReadBytes;
    }

    public void setMaxReadBytes(int maxReadBytes) {
        this.maxReadBytes = maxReadBytes;
    }

    public int getMaxDiffLines() {
        return maxDiffLines;
    }

    public void setMaxDiffLines(int maxDiffLines) {
        this.maxDiffLines = maxDiffLines;
    }

    public int getMaxPatchBytes() {
        return maxPatchBytes;
    }

    public void setMaxPatchBytes(int maxPatchBytes) {
        this.maxPatchBytes = maxPatchBytes;
    }

    public int getMaxPatchHunks() {
        return maxPatchHunks;
    }

    public void setMaxPatchHunks(int maxPatchHunks) {
        this.maxPatchHunks = maxPatchHunks;
    }

    public int getMaxTestTimeoutSeconds() {
        return maxTestTimeoutSeconds;
    }

    public void setMaxTestTimeoutSeconds(int maxTestTimeoutSeconds) {
        this.maxTestTimeoutSeconds = maxTestTimeoutSeconds;
    }

    public Duration getPatchConfirmTtl() {
        return patchConfirmTtl;
    }

    public void setPatchConfirmTtl(Duration patchConfirmTtl) {
        this.patchConfirmTtl = patchConfirmTtl;
    }

    public int getMaxPendingConfirmations() {
        return maxPendingConfirmations;
    }

    public void setMaxPendingConfirmations(int maxPendingConfirmations) {
        this.maxPendingConfirmations = maxPendingConfirmations;
    }

    public String getTestCommand() {
        return testCommand;
    }

    public void setTestCommand(String testCommand) {
        this.testCommand = testCommand;
    }

    public Duration getTestTimeout() {
        return testTimeout;
    }

    public void setTestTimeout(Duration testTimeout) {
        this.testTimeout = testTimeout;
    }

    @AssertTrue(message = "代码工作区必须配置至少一个允许仓库")
    public boolean hasAllowedRepositories() {
        return !enabled || allowedRepositories != null && !allowedRepositories.isEmpty();
    }

    @AssertTrue(message = "测试命令不能为空")
    public boolean hasTestCommand() {
        return !enabled || testCommand != null && !testCommand.isBlank();
    }
}
