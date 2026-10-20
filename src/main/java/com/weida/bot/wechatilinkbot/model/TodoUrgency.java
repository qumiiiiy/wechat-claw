package com.weida.bot.wechatilinkbot.model;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Time-based urgency classification for pending student todos. */
public enum TodoUrgency {
    OVERDUE,
    TODAY,
    UPCOMING;

    /**
     * Classifies one pending todo relative to {@code clock}.
     *
     * <p>Returns {@code null} when the todo is already completed — urgency
     * is only meaningful for items that still need attention.</p>
     */
    public static TodoUrgency classify(StudentTodo todo, Clock clock) {
        if (todo == null || todo.status() != StudentTodoStatus.PENDING) {
            return null;
        }
        Instant now = Instant.now(clock);
        Instant due = todo.dueTime();
        if (due == null) {
            return null;
        }
        if (due.isBefore(now)) {
            return OVERDUE;
        }
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate dueDay = LocalDate.ofInstant(due, zone);
        if (dueDay.isEqual(today)) {
            return TODAY;
        }
        return UPCOMING;
    }
}
