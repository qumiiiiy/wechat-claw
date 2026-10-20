package com.weida.bot.wechatilinkbot.capability;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Per-conversation reminder flags set by the scheduler and consumed by tools.
 *
 * <p>Flags are cleared after the first read — the message that triggered the read
 * carries the reminder, so the flag is no longer needed.</p>
 */
@Component
public class ReminderState {

    private static final Logger LOG = LoggerFactory.getLogger(ReminderState.class);

    private final ConcurrentHashMap<String, ReminderFlags> flags = new ConcurrentHashMap<>();

    /** Records today-due items for one conversation. */
    public void markTodayItems(String conversationId, int count, String sampleTitle) {
        flags.merge(conversationId,
                new ReminderFlags(0, 0, null, 0, count, sampleTitle),
                (existing, incoming) -> new ReminderFlags(
                        existing.overdueCount(),
                        existing.oldestOverdueDays(),
                        existing.oldestOverdueTitle(),
                        existing.escalatedOverdueCount(),
                        incoming.todayCount(),
                        incoming.todaySampleTitle()));
        LOG.debug("reminder_today_marked conversation={} count={}", conversationReference(conversationId), count);
    }

    /** Records plain overdue items for one conversation. */
    public void markOverdue(String conversationId, int count, String sampleTitle) {
        flags.merge(conversationId,
                new ReminderFlags(count, 0, sampleTitle, 0, 0, null),
                (existing, incoming) -> new ReminderFlags(
                        incoming.overdueCount(),
                        existing.oldestOverdueDays(),
                        existing.oldestOverdueTitle(),
                        existing.escalatedOverdueCount(),
                        existing.todayCount(),
                        existing.todaySampleTitle()));
        LOG.debug("reminder_overdue_marked conversation={} count={}",
                conversationReference(conversationId), count);
    }

    /** Records escalated overdue items (逾期超48h) for one conversation. */
    public void markEscalatedOverdue(
            String conversationId, int count, String oldestTitle, long days) {
        flags.merge(conversationId,
                new ReminderFlags(count, days, oldestTitle, count, 0, null),
                (existing, incoming) -> new ReminderFlags(
                        incoming.overdueCount(),
                        incoming.oldestOverdueDays(),
                        incoming.oldestOverdueTitle(),
                        incoming.escalatedOverdueCount(),
                        existing.todayCount(),
                        existing.todaySampleTitle()));
        LOG.info("reminder_overdue_escalated conversation={} count={} oldestDays={}",
                conversationReference(conversationId), count, days);
    }

    /** Returns the current flags for a conversation and clears them. Never null. */
    public ReminderFlags consume(String conversationId) {
        ReminderFlags removed = flags.remove(conversationId);
        return removed == null ? ReminderFlags.EMPTY : removed;
    }

    /** Returns all conversations that have active reminders. */
    public Set<String> activeConversations() {
        return Set.copyOf(flags.keySet());
    }

    /** Returns total active conversations for health logging. */
    public int activeCount() {
        return flags.size();
    }

    static String conversationReference(String conversationId) {
        if (conversationId == null) {
            return "null";
        }
        return Integer.toUnsignedString(conversationId.hashCode(), 16);
    }
}
