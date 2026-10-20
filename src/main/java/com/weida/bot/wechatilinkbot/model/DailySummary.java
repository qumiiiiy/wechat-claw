package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Read-only snapshot summarizing a session's pending student todos.
 */
public record DailySummary(
        Instant generatedAt,
        List<TodoItem> overdue,
        List<TodoItem> today,
        List<TodoItem> tomorrow,
        List<TodoItem> thisWeek,
        int totalPending,
        int truncated) {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    public DailySummary {
        if (generatedAt == null) {
            throw new IllegalArgumentException("生成时间不能为空");
        }
        overdue = List.copyOf(overdue == null ? List.of() : overdue);
        today = List.copyOf(today == null ? List.of() : today);
        tomorrow = List.copyOf(tomorrow == null ? List.of() : tomorrow);
        thisWeek = List.copyOf(thisWeek == null ? List.of() : thisWeek);
        if (totalPending < 0 || truncated < 0) {
            throw new IllegalArgumentException("计数不能为负数");
        }
    }

    /** Formats a compact "today's priorities" text suitable for WeChat. */
    public String format() {
        if (totalPending == 0) {
            return "📋 当前没有待办事项。";
        }
        var sb = new StringBuilder("📋 今日要务\n");
        appendSection(sb, "⚠️ 逾期", overdue);
        appendSection(sb, "📅 今天截止", today);
        appendSection(sb, "🔜 明天截止", tomorrow);
        appendSection(sb, "📆 本周截止", thisWeek);
        sb.append("共 ").append(totalPending).append(" 项待办");
        if (truncated > 0) {
            sb.append("（已省略 ").append(truncated).append(" 项）");
        }
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String label, List<TodoItem> items) {
        if (items.isEmpty()) {
            return;
        }
        sb.append(label).append("（").append(items.size()).append("项）：\n");
        for (TodoItem item : items) {
            sb.append("  · ").append(item.title())
                    .append(" — ").append(FMT.format(item.dueTime())).append('\n');
        }
    }

    /**
     * Lightweight reference to one todo item within the summary.
     */
    public record TodoItem(String id, String title, Instant dueTime, TodoUrgency urgency) {
        public TodoItem {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("待办 ID 不能为空");
            }
            if (title == null) {
                throw new IllegalArgumentException("待办标题不能为空");
            }
            if (dueTime == null) {
                throw new IllegalArgumentException("截止时间不能为空");
            }
            if (urgency == null) {
                throw new IllegalArgumentException("紧急度不能为空");
            }
            id = id.strip();
            title = title.strip();
        }
    }
}
