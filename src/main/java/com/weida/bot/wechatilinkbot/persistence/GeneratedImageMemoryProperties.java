package com.weida.bot.wechatilinkbot.persistence;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bounds for transient generated-image references.
 */
@Validated
@ConfigurationProperties(prefix = "bot.persistence.generated-image")
public class GeneratedImageMemoryProperties {

    private boolean enabled = true;

    @NotNull
    private Duration expireAfter = Duration.ofMinutes(30);

    @Min(1)
    @Max(1000)
    private int maxConversations = 50;

    @Min(1024)
    @Max(52_428_800)
    private int maxImageBytes = 7 * 1024 * 1024;

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

    public int getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(int maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public long getMaxTotalBytes() {
        return maxTotalBytes;
    }

    public void setMaxTotalBytes(long maxTotalBytes) {
        this.maxTotalBytes = maxTotalBytes;
    }

    @AssertTrue(message = "生成图片记忆过期时间必须为正，且单图上限不能大于总上限")
    public boolean isValid() {
        return expireAfter != null
                && !expireAfter.isZero()
                && !expireAfter.isNegative()
                && maxImageBytes <= maxTotalBytes;
    }
}
