package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;

/**
 * Stable metadata used to identify one inbound message without carrying its
 * reply token or payload across the channel boundary.
 */
public record InboundMessageIdentity(
        String botId,
        long messageId,
        String senderId,
        String conversationId,
        Instant receivedAt) {

    private static final int MAX_TEXT_LENGTH = 255;

    public InboundMessageIdentity {
        botId = requireText(botId, "botId");
        senderId = requireText(senderId, "senderId");
        conversationId = requireText(conversationId, "conversationId");
        if (messageId < 0) {
            throw new IllegalArgumentException("messageId 不能为负数");
        }
        if (receivedAt == null) {
            throw new IllegalArgumentException("receivedAt 不能为空");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + " 长度不能超过 255");
        }
        return normalized;
    }
}
