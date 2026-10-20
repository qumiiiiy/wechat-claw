package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Unified read-only view of one pending item from any source.
 *
 * <p>Sequence numbers are assigned by {@code PendingItemService} at query time
 * and are stable within a single response. The {@code internalId} is kept for
 * programmatic operations but never exposed to the user.</p>
 */
public record PendingItem(
        int sequenceNumber,
        String internalId,
        String title,
        PendingItemSource source,
        StudentTodoStatus status,
        Instant dueTime,
        String note) {

    private static final DateTimeFormatter DUE_FMT =
            DateTimeFormatter.ofPattern("M月d日 HH:mm")
                    .withZone(ZoneId.of("Asia/Shanghai"));

    public PendingItem {
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("序号不能为负数");
        }
        if (internalId == null || internalId.isBlank()) {
            throw new IllegalArgumentException("internalId 不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (source == null) {
            throw new IllegalArgumentException("来源不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("状态不能为空");
        }
        if (dueTime == null) {
            throw new IllegalArgumentException("截止时间不能为空");
        }
        title = title.strip();
    }

    /** Formats one line of a list display, without the internal ID. */
    public String formatListItem() {
        String sourceLabel = sourceLabel();
        String dueLabel = dueTime.equals(Instant.MAX)
                ? "无截止时间"
                : dueTime.equals(Instant.MIN)
                ? "无开始时间"
                : DUE_FMT.format(dueTime);
        String result = String.format("%d. %s — %s  [%s]",
                sequenceNumber, title, dueLabel, sourceLabel);
        if (note != null && !note.isBlank()) {
            result += " ⚠️" + note;
        }
        return result;
    }

    /** Compact reference usable in action prompts. */
    public String naturalRef() {
        return "第" + sequenceNumber + "项";
    }

    private String sourceLabel() {
        return switch (source) {
            case PERSONAL -> "个人";
            case EMAIL -> "邮箱";
            case COURSE -> "学习通";
        };
    }
}
