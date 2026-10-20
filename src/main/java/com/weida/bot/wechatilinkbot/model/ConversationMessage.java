package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;

/**
 * Stable conversation message representation shared by agent and persistence.
 *
 * @param id optional persistent identifier
 * @param conversationId conversation identity
 * @param role model role
 * @param content bounded message content
 * @param createdAt creation time
 * @param updatedAt last update time
 */
public record ConversationMessage(
        Long id,
        String conversationId,
        String role,
        String content,
        Instant createdAt,
        Instant updatedAt) {
}
