package com.weida.bot.wechatilinkbot.infrastructure.ratelimit;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Fixed-window per-conversation and process-wide inbound message limiter.
 *
 * @author ryx
 * @since 2026-07-21
 */
@Component
public class MessageRateLimiter {

    private static final long WINDOW_SECONDS = 60L;

    private final RateLimitProperties properties;
    private final Map<String, WindowCounter> userCounterMap = new HashMap<>();

    private WindowCounter globalCounter = new WindowCounter(Long.MIN_VALUE, 0, false);

    public MessageRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    /** Checks and consumes one inbound-message permit. */
    public synchronized RateLimitDecision acquire(String conversationId) {
        if (!properties.isEnabled()) {
            return RateLimitDecision.ALLOWED;
        }
        String safeConversationId = conversationId == null ? "unknown" : conversationId;
        long window = currentWindow();
        resetWindowIfRequired(window);
        if (globalCounter.count() >= properties.getGlobalRequestsPerMinute()) {
            RateLimitDecision decision = globalCounter.notified()
                    ? RateLimitDecision.REJECTED_SILENT
                    : RateLimitDecision.REJECTED_NOTIFY;
            globalCounter = globalCounter.withNotified();
            return decision;
        }
        globalCounter = globalCounter.incremented();
        return acquireUser(safeConversationId, window);
    }

    long currentWindow() {
        return Instant.now().getEpochSecond() / WINDOW_SECONDS;
    }

    private void resetWindowIfRequired(long window) {
        if (globalCounter.window() != window) {
            globalCounter = new WindowCounter(window, 0, false);
            userCounterMap.clear();
        }
    }

    private RateLimitDecision acquireUser(String conversationId, long window) {
        WindowCounter counter = userCounterMap.getOrDefault(
                conversationId, new WindowCounter(window, 0, false));
        if (counter.count() >= properties.getRequestsPerConversationPerMinute()) {
            RateLimitDecision decision = counter.notified()
                    ? RateLimitDecision.REJECTED_SILENT
                    : RateLimitDecision.REJECTED_NOTIFY;
            userCounterMap.put(conversationId, counter.withNotified());
            return decision;
        }
        userCounterMap.put(conversationId, counter.incremented());
        return RateLimitDecision.ALLOWED;
    }

    private record WindowCounter(long window, int count, boolean notified) {

        private WindowCounter incremented() {
            return new WindowCounter(window, count + 1, notified);
        }

        private WindowCounter withNotified() {
            return notified ? this : new WindowCounter(window, count, true);
        }
    }
}
