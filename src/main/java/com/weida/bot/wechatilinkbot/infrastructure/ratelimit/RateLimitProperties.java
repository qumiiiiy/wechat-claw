package com.weida.bot.wechatilinkbot.infrastructure.ratelimit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Process-local inbound request quota.
 */
@Validated
@ConfigurationProperties(prefix = "bot.infrastructure.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    @Min(1)
    @Max(10_000)
    private int requestsPerConversationPerMinute = 20;

    @Min(1)
    @Max(100_000)
    private int globalRequestsPerMinute = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRequestsPerConversationPerMinute() {
        return requestsPerConversationPerMinute;
    }

    public void setRequestsPerConversationPerMinute(int value) {
        requestsPerConversationPerMinute = value;
    }

    public int getGlobalRequestsPerMinute() {
        return globalRequestsPerMinute;
    }

    public void setGlobalRequestsPerMinute(int globalRequestsPerMinute) {
        this.globalRequestsPerMinute = globalRequestsPerMinute;
    }
}
