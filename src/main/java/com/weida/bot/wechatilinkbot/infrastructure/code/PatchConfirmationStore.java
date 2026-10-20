package com.weida.bot.wechatilinkbot.infrastructure.code;

import com.weida.bot.wechatilinkbot.model.CodeFileSnapshot;
import com.weida.bot.wechatilinkbot.model.PatchConfirmation;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * In-memory store for patch confirmations.
 *
 * <p>Each confirmation is bound to a conversation, single-use, and short-lived.
 * Confirmations are automatically cleaned up when expired or consumed.</p>
 */
@Component
public class PatchConfirmationStore {

    private final CodeWorkspaceProperties properties;
    private final Clock clock;
    private final Map<String, PatchConfirmation> store = new LinkedHashMap<>();

    public PatchConfirmationStore() {
        this(null, Clock.systemUTC());
    }

    public PatchConfirmationStore(CodeWorkspaceProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public PatchConfirmationStore(CodeWorkspaceProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates a new patch confirmation bound to the given conversation. */
    public PatchConfirmation create(
            String conversationId,
            String fileId,
            CodeFileSnapshot snapshot,
            String diff) {
        enforceLimit();
        Instant now = clock.instant();
        String confirmationId = "patch-" + UUID.randomUUID();
        PatchConfirmation confirmation = new PatchConfirmation(
                confirmationId,
                conversationId,
                fileId,
                snapshot,
                diff,
                now,
                now.plus(properties.getPatchConfirmTtl()),
                false);
        synchronized (store) {
            store.put(confirmationId, confirmation);
        }
        return confirmation;
    }

    /**
     * Validates and consumes a confirmation. Returns the confirmation if valid.
     *
     * @throws IllegalArgumentException if the confirmation is invalid, expired,
     *         already consumed, or belongs to a different conversation
     */
    public PatchConfirmation validateAndConsume(String confirmationId, String conversationId) {
        synchronized (store) {
            removeExpired();
            PatchConfirmation confirmation = store.get(confirmationId);
            if (confirmation == null) {
                throw new IllegalArgumentException("确认 ID 无效或已过期");
            }
            if (!confirmation.conversationId().equals(conversationId)) {
                throw new IllegalArgumentException("确认 ID 不属于当前会话");
            }
            if (confirmation.consumed()) {
                throw new IllegalArgumentException("确认 ID 已被使用，不可重复应用");
            }
            if (confirmation.expiresAt().isBefore(clock.instant())) {
                store.remove(confirmationId);
                throw new IllegalArgumentException("确认 ID 已过期，请重新生成 diff");
            }
            // Mark as consumed (single-use)
            PatchConfirmation consumed = new PatchConfirmation(
                    confirmation.confirmationId(),
                    confirmation.conversationId(),
                    confirmation.fileId(),
                    confirmation.snapshot(),
                    confirmation.diff(),
                    confirmation.createdAt(),
                    confirmation.expiresAt(),
                    true);
            store.put(confirmationId, consumed);
            return consumed;
        }
    }

    /** Gets a confirmation without consuming it (for validation checks). */
    public PatchConfirmation get(String confirmationId) {
        synchronized (store) {
            removeExpired();
            return store.get(confirmationId);
        }
    }

    /** Removes all confirmations for a conversation. */
    public void clearConversation(String conversationId) {
        synchronized (store) {
            store.entrySet().removeIf(entry ->
                    entry.getValue().conversationId().equals(conversationId));
        }
    }

    private void enforceLimit() {
        synchronized (store) {
            removeExpired();
            while (store.size() >= properties.getMaxPendingConfirmations()) {
                Iterator<String> iterator = store.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
    }

    private void removeExpired() {
        Instant now = clock.instant();
        store.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
