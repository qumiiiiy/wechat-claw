package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;

/**
 * One-time, short-lived patch confirmation bound to a conversation.
 *
 * @param confirmationId opaque confirmation ID
 * @param conversationId conversation that created this confirmation
 * @param fileId the file this confirmation is for
 * @param snapshot snapshot of the file at confirmation time
 * @param diff unified diff to apply
 * @param createdAt when the confirmation was created
 * @param expiresAt when the confirmation expires
 * @param consumed whether this confirmation has been used
 */
public record PatchConfirmation(
        String confirmationId,
        String conversationId,
        String fileId,
        CodeFileSnapshot snapshot,
        String diff,
        Instant createdAt,
        Instant expiresAt,
        boolean consumed) {
}
