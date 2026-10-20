package com.weida.bot.wechatilinkbot.persistence;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bounds for transient generated-file references used by email attachments. */
@Validated
@ConfigurationProperties(prefix = "bot.persistence.generated-file")
public class GeneratedFileMemoryProperties {

    private boolean enabled = true;

    @NotNull
    private Duration expireAfter = Duration.ofMinutes(30);

    @Min(1)
    @Max(1000)
    private int maxConversations = 50;

    @Min(1024)
    @Max(52_428_800)
    private int maxFileBytes = 10 * 1024 * 1024;

    @Min(1024)
    private long maxTotalBytes = 64L * 1024L * 1024L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getExpireAfter() {
        return expireAfter;
    }

    public void setExpireAfter(Duration expireAfter) {
        this.expireAfter = expireAfter;
    }

    public int getMaxConversations() {
        return maxConversations;
    }

    public void setMaxConversations(int maxConversations) {
        this.maxConversations = maxConversations;
    }

    public int getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(int maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public long getMaxTotalBytes() {
        return maxTotalBytes;
    }

    public void setMaxTotalBytes(long maxTotalBytes) {
        this.maxTotalBytes = maxTotalBytes;
    }

    @AssertTrue(message = "生成文件记忆过期时间必须为正，且单文件上限不能大于总上限")
    public boolean isValid() {
        return expireAfter != null
                && !expireAfter.isZero()
                && !expireAfter.isNegative()
                && maxFileBytes <= maxTotalBytes;
    }
}
