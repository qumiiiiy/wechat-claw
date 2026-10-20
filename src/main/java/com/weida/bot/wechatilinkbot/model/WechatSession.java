package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;

/**
 * SDK-neutral snapshot of a managed WeChat login session.
 */
public record WechatSession(
        String sessionId,
        Boolean enabled,
        String loginStatus,
        String connectionStatus,
        String botId,
        String ownerUserId,
        Boolean qrCodeAvailable,
        Instant createdAt,
        Instant qrCodeGeneratedAt,
        Instant lastPollStartedAt,
        Instant lastPollCompletedAt,
        Instant lastMessageReceivedAt,
        Long receivedMessageCount,
        String lastError) {
}
