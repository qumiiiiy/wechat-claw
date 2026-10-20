package com.weida.bot.wechatilinkbot.channel.web;

import com.weida.bot.wechatilinkbot.model.WechatSession;
import java.time.Instant;

/**
 * Public HTTP representation of a managed WeChat Bot session.
 *
 * @author ryx
 * @since 2026-07-20
 */
public record WechatSessionView(
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

    /**
     * Maps the internal session DTO to its HTTP view.
     *
     * @param source internal session state
     * @return HTTP view object
     */
    public static WechatSessionView from(WechatSession source) {
        return new WechatSessionView(
                source.sessionId(),
                source.enabled(),
                source.loginStatus(),
                source.connectionStatus(),
                source.botId(),
                source.ownerUserId(),
                source.qrCodeAvailable(),
                source.createdAt(),
                source.qrCodeGeneratedAt(),
                source.lastPollStartedAt(),
                source.lastPollCompletedAt(),
                source.lastMessageReceivedAt(),
                source.receivedMessageCount(),
                source.lastError());
    }
}
