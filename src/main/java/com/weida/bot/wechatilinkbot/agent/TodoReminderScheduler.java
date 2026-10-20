package com.weida.bot.wechatilinkbot.agent;

import com.weida.bot.wechatilinkbot.capability.ReminderState;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import com.weida.bot.wechatilinkbot.model.TodoUrgency;
import com.weida.bot.wechatilinkbot.persistence.StudentTodoMemory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic scanner that updates reminder flags without sending any WeChat messages.
 *
 * <p>Runs daily at 08:00 to pre-compute today-due and escalated-overdue items. Also
 * prunes completed todos older than 7 days.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "bot.agent.todo-reminder",
        name = "enabled",
        havingValue = "true")
public class TodoReminderScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(TodoReminderScheduler.class);
    private static final Duration ESCALATION_THRESHOLD = Duration.ofHours(48);
    private static final Duration COMPLETED_RETENTION = Duration.ofDays(7);

    private final StudentTodoMemory memory;
    private final ReminderState state;
    private final Clock clock;

    @Autowired
    public TodoReminderScheduler(@Lazy StudentTodoMemory memory, ReminderState state) {
        this(memory, state, Clock.systemUTC());
    }

    TodoReminderScheduler(StudentTodoMemory memory, ReminderState state, Clock clock) {
        this.memory = memory;
        this.state = state;
        this.clock = clock;
    }

    /** Per-minute overdue poll: marks reminders for immediate detection without waiting for the daily scan. */
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    public void pollOverdue() {
        Instant now = Instant.now(clock);
        LOG.debug("todo_overdue_poll_started time={}", now);
        for (String conversationId : memory.allConversationIds()) {
            List<StudentTodo> pending = memory.listPending(conversationId);
            if (pending.isEmpty()) {
                continue;
            }
            markOverdueReminders(conversationId, pending, now);
        }
        LOG.debug("todo_overdue_poll_completed conversations={}", memory.allConversationIds().size());
    }

    /** Daily scan: classify todos, mark reminders, prune old completed todos. */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Shanghai")
    public void scanDaily() {
        Instant now = Instant.now(clock);
        LOG.info("todo_reminder_scan_started time={}", now);
        int totalPending = 0;
        int conversationsWithReminders = 0;

        for (String conversationId : memory.allConversationIds()) {
            List<StudentTodo> pending = memory.listPending(conversationId);
            if (pending.isEmpty()) {
                continue;
            }
            totalPending += pending.size();
            boolean marked = markReminders(conversationId, pending, now);
            if (marked) {
                conversationsWithReminders++;
            }
        }

        int pruned = memory.deleteCompletedOlderThan(now.minus(COMPLETED_RETENTION));

        LOG.info(
                "todo_reminder_scan_completed "
                        + "conversationsTotal={} conversationsWithReminders={} "
                        + "pendingTotal={} completedPruned={}",
                memory.allConversationIds().size(),
                conversationsWithReminders,
                totalPending,
                pruned);
    }

    private boolean markReminders(String conversationId, List<StudentTodo> pending, Instant now) {
        markOverdueReminders(conversationId, pending, now);
        markTodayReminders(conversationId, pending, now);
        return pending.stream().anyMatch(t -> t.dueTime().isBefore(now))
                || pending.stream().anyMatch(t -> !t.dueTime().isBefore(now)
                        && TodoUrgency.classify(t, clock) == TodoUrgency.TODAY);
    }

    /** Marks overdue items for one conversation; idempotent — repeated calls overwrite. */
    void markOverdueReminders(String conversationId, List<StudentTodo> pending, Instant now) {
        var overdueList = pending.stream()
                .filter(t -> t.dueTime().isBefore(now))
                .sorted(Comparator.comparing(StudentTodo::dueTime))
                .toList();
        if (overdueList.isEmpty()) {
            return;
        }
        var escalated = overdueList.stream()
                .filter(t -> Duration.between(t.dueTime(), now).compareTo(ESCALATION_THRESHOLD) > 0)
                .toList();
        if (!escalated.isEmpty()) {
            StudentTodo oldest = overdueList.get(0);
            long days = Duration.between(oldest.dueTime(), now).toDays();
            state.markEscalatedOverdue(
                    conversationId, overdueList.size(), oldest.title(), Math.max(1, days));
        } else {
            state.markOverdue(
                    conversationId, overdueList.size(), overdueList.get(0).title());
        }
    }

    /** Marks today-due items for one conversation. */
    private void markTodayReminders(String conversationId, List<StudentTodo> pending, Instant now) {
        var todayList = pending.stream()
                .filter(t -> !t.dueTime().isBefore(now)
                        && TodoUrgency.classify(t, clock) == TodoUrgency.TODAY)
                .sorted(Comparator.comparing(StudentTodo::dueTime))
                .toList();
        if (!todayList.isEmpty()) {
            state.markTodayItems(conversationId, todayList.size(), todayList.get(0).title());
        }
    }
}
