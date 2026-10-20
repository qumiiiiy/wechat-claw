package com.weida.bot.wechatilinkbot.channel.ilink;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated configuration for WeChat sessions, polling, and bounded workers.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Validated
@ConfigurationProperties(prefix = "bot.channel.wechat")
public class IlinkProperties {

    private boolean enabled = true;
    private boolean autoOpenLoginPage = true;
    private boolean autoReplyEnabled = true;

    @Min(100)
    @Max(10_000)
    private int autoReplyFileThresholdCharacters = 1500;

    @Min(1)
    @Max(10_000)
    private int maxPendingReplies = 1000;

    @Min(1)
    @Max(10_000)
    private int maxPendingReplyCharacters = 500;

    @NotNull
    private Duration pollInterval = Duration.ofSeconds(1);

    @NotNull
    private Duration pollFailureDelay = Duration.ofSeconds(5);

    @NotNull
    private Duration loginTimeout = Duration.ofMinutes(3);

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(35);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(40);

    @NotNull
    private Duration writeTimeout = Duration.ofSeconds(35);

    @Min(200)
    @Max(1000)
    private int qrCodeSize = 360;

    @Min(1)
    @Max(1000)
    private int maxBotSessions = 20;

    @Min(1)
    @Max(64)
    private int messageHandlingCoreThreads = 2;

    @Min(1)
    @Max(256)
    private int messageHandlingMaxThreads = 16;

    @Min(1)
    @Max(10_000)
    private int messageHandlingQueueCapacity = 256;

    @Min(1)
    @Max(20)
    private int messageRetryMaxAttempts = 5;

    @NotNull
    private Duration messageRetryInitialDelay = Duration.ofSeconds(2);

    @NotNull
    private Duration messageRetryMaxDelay = Duration.ofMinutes(5);

    @NotNull
    private Duration messageProcessingLease = Duration.ofMinutes(5);

    @NotNull
    private Duration workerKeepAlive = Duration.ofSeconds(60);

    @NotBlank
    private String channelVersion = "1.0.0";

    private boolean mediaDownloadEnabled;

    @NotNull
    private Path mediaDownloadDirectory = Path.of("downloads", "wechat");

    /**
     * When {@code true}, image-kind media (PlantUML diagrams, qwen-image
     * outputs) are delivered through {@code client.sendFile()} instead of
     * {@code client.sendImage()}. Use this as a fallback when the iLink image
     * CDN endpoint returns HTTP 500 — the file CDN path uses a different
     * upload code path inside the SDK and is empirically more reliable.
     */
    private boolean deliverImagesAsFiles = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoOpenLoginPage() {
        return autoOpenLoginPage;
    }

    public void setAutoOpenLoginPage(boolean autoOpenLoginPage) {
        this.autoOpenLoginPage = autoOpenLoginPage;
    }

    public boolean isAutoReplyEnabled() {
        return autoReplyEnabled;
    }

    public void setAutoReplyEnabled(boolean autoReplyEnabled) {
        this.autoReplyEnabled = autoReplyEnabled;
    }

    public int getAutoReplyFileThresholdCharacters() {
        return autoReplyFileThresholdCharacters;
    }

    public void setAutoReplyFileThresholdCharacters(
            int autoReplyFileThresholdCharacters) {
        this.autoReplyFileThresholdCharacters = autoReplyFileThresholdCharacters;
    }

    public int getMaxPendingReplies() {
        return maxPendingReplies;
    }

    public void setMaxPendingReplies(int maxPendingReplies) {
        this.maxPendingReplies = maxPendingReplies;
    }

    public int getMaxPendingReplyCharacters() {
        return maxPendingReplyCharacters;
    }

    public void setMaxPendingReplyCharacters(int maxPendingReplyCharacters) {
        this.maxPendingReplyCharacters = maxPendingReplyCharacters;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration getPollFailureDelay() {
        return pollFailureDelay;
    }

    public void setPollFailureDelay(Duration pollFailureDelay) {
        this.pollFailureDelay = pollFailureDelay;
    }

    public Duration getLoginTimeout() {
        return loginTimeout;
    }

    public void setLoginTimeout(Duration loginTimeout) {
        this.loginTimeout = loginTimeout;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(Duration writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public int getQrCodeSize() {
        return qrCodeSize;
    }

    public void setQrCodeSize(int qrCodeSize) {
        this.qrCodeSize = qrCodeSize;
    }

    public String getChannelVersion() {
        return channelVersion;
    }

    public void setChannelVersion(String channelVersion) {
        this.channelVersion = channelVersion;
    }

    public int getMaxBotSessions() {
        return maxBotSessions;
    }

    public void setMaxBotSessions(int maxBotSessions) {
        this.maxBotSessions = maxBotSessions;
    }

    public int getMessageHandlingCoreThreads() {
        return messageHandlingCoreThreads;
    }

    public void setMessageHandlingCoreThreads(int messageHandlingCoreThreads) {
        this.messageHandlingCoreThreads = messageHandlingCoreThreads;
    }

    public int getMessageHandlingMaxThreads() {
        return messageHandlingMaxThreads;
    }

    public void setMessageHandlingMaxThreads(int messageHandlingMaxThreads) {
        this.messageHandlingMaxThreads = messageHandlingMaxThreads;
    }

    public int getMessageHandlingQueueCapacity() {
        return messageHandlingQueueCapacity;
    }

    public void setMessageHandlingQueueCapacity(int messageHandlingQueueCapacity) {
        this.messageHandlingQueueCapacity = messageHandlingQueueCapacity;
    }

    public int getMessageRetryMaxAttempts() {
        return messageRetryMaxAttempts;
    }

    public void setMessageRetryMaxAttempts(int messageRetryMaxAttempts) {
        this.messageRetryMaxAttempts = messageRetryMaxAttempts;
    }

    public Duration getMessageRetryInitialDelay() {
        return messageRetryInitialDelay;
    }

    public void setMessageRetryInitialDelay(Duration messageRetryInitialDelay) {
        this.messageRetryInitialDelay = messageRetryInitialDelay;
    }

    public Duration getMessageRetryMaxDelay() {
        return messageRetryMaxDelay;
    }

    public void setMessageRetryMaxDelay(Duration messageRetryMaxDelay) {
        this.messageRetryMaxDelay = messageRetryMaxDelay;
    }

    public Duration getMessageProcessingLease() {
        return messageProcessingLease;
    }

    public void setMessageProcessingLease(Duration messageProcessingLease) {
        this.messageProcessingLease = messageProcessingLease;
    }

    public Duration getWorkerKeepAlive() {
        return workerKeepAlive;
    }

    public void setWorkerKeepAlive(Duration workerKeepAlive) {
        this.workerKeepAlive = workerKeepAlive;
    }

    @AssertTrue(message = "消息处理核心线程数不能大于最大线程数")
    public boolean isMessageHandlingPoolValid() {
        return messageHandlingCoreThreads <= messageHandlingMaxThreads;
    }

    @AssertTrue(message = "工作线程空闲回收时间必须大于 0")
    public boolean isWorkerKeepAliveValid() {
        return workerKeepAlive != null
                && !workerKeepAlive.isZero()
                && !workerKeepAlive.isNegative();
    }

    @AssertTrue(message = "消息重试延迟和处理租约必须大于 0，且最大延迟不能小于初始延迟")
    public boolean isMessageRetryPolicyValid() {
        return messageRetryInitialDelay != null
                && messageRetryMaxDelay != null
                && messageProcessingLease != null
                && !messageRetryInitialDelay.isZero()
                && !messageRetryInitialDelay.isNegative()
                && !messageRetryMaxDelay.isZero()
                && !messageRetryMaxDelay.isNegative()
                && !messageProcessingLease.isZero()
                && !messageProcessingLease.isNegative()
                && messageRetryMaxDelay.compareTo(messageRetryInitialDelay) >= 0;
    }

    public boolean isMediaDownloadEnabled() {
        return mediaDownloadEnabled;
    }

    public void setMediaDownloadEnabled(boolean mediaDownloadEnabled) {
        this.mediaDownloadEnabled = mediaDownloadEnabled;
    }

    public Path getMediaDownloadDirectory() {
        return mediaDownloadDirectory;
    }

    public void setMediaDownloadDirectory(Path mediaDownloadDirectory) {
        this.mediaDownloadDirectory = mediaDownloadDirectory;
    }

    public boolean isDeliverImagesAsFiles() {
        return deliverImagesAsFiles;
    }

    public void setDeliverImagesAsFiles(boolean deliverImagesAsFiles) {
        this.deliverImagesAsFiles = deliverImagesAsFiles;
    }
}
