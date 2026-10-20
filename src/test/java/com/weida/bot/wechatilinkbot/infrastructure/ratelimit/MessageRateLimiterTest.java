package com.weida.bot.wechatilinkbot.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MessageRateLimiterTest {

    @Test
    void limitsEachConversationAndNotifiesOnlyOncePerWindow() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRequestsPerConversationPerMinute(2);
        properties.setGlobalRequestsPerMinute(100);
        AtomicLong window = new AtomicLong(10L);
        MessageRateLimiter limiter = limiter(properties, window);

        assertThat(limiter.acquire("user-a")).isEqualTo(RateLimitDecision.ALLOWED);
        assertThat(limiter.acquire("user-a")).isEqualTo(RateLimitDecision.ALLOWED);
        assertThat(limiter.acquire("user-a"))
                .isEqualTo(RateLimitDecision.REJECTED_NOTIFY);
        assertThat(limiter.acquire("user-a"))
                .isEqualTo(RateLimitDecision.REJECTED_SILENT);

        window.incrementAndGet();
        assertThat(limiter.acquire("user-a")).isEqualTo(RateLimitDecision.ALLOWED);
    }

    @Test
    void appliesTheProcessWideLimitAcrossConversations() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRequestsPerConversationPerMinute(100);
        properties.setGlobalRequestsPerMinute(2);
        MessageRateLimiter limiter = limiter(properties, new AtomicLong(20L));

        assertThat(limiter.acquire("user-a")).isEqualTo(RateLimitDecision.ALLOWED);
        assertThat(limiter.acquire("user-b")).isEqualTo(RateLimitDecision.ALLOWED);
        assertThat(limiter.acquire("user-c"))
                .isEqualTo(RateLimitDecision.REJECTED_NOTIFY);
        assertThat(limiter.acquire("user-d"))
                .isEqualTo(RateLimitDecision.REJECTED_SILENT);
    }

    private static MessageRateLimiter limiter(
            RateLimitProperties properties,
            AtomicLong window) {
        return new MessageRateLimiter(properties) {
            @Override
            long currentWindow() {
                return window.get();
            }
        };
    }
}
