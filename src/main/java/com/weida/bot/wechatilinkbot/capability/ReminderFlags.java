package com.weida.bot.wechatilinkbot.capability;

/**
 * Snapshot of reminder state for one conversation.
 *
 * @param overdueCount         pending todos already past due
 * @param oldestOverdueDays    age (in days) of the most-ancient overdue item
 * @param oldestOverdueTitle   title of the most-ancient overdue item, or null
 * @param escalatedOverdueCount overdu items older than 48 hours
 * @param todayCount           pending todos due today
 * @param todaySampleTitle     title of one today-due item, or null
 */
public record ReminderFlags(
        int overdueCount,
        long oldestOverdueDays,
        String oldestOverdueTitle,
        int escalatedOverdueCount,
        int todayCount,
        String todaySampleTitle) {

    static final ReminderFlags EMPTY = new ReminderFlags(0, 0, null, 0, 0, null);

    public ReminderFlags {
        if (overdueCount < 0 || oldestOverdueDays < 0
                || escalatedOverdueCount < 0 || todayCount < 0) {
            throw new IllegalArgumentException("提醒计数不能为负数");
        }
    }

    public boolean isEmpty() {
        return overdueCount == 0 && todayCount == 0;
    }

    /** Formats a compact reminder string for appending to a tool response. */
    public String format() {
        if (isEmpty()) {
            return "";
        }
        var sb = new StringBuilder("\n\n══════════════════════════\n📌 待办提醒：\n");
        if (escalatedOverdueCount > 0) {
            sb.append("⚠️ 严重逾期（")
                    .append(escalatedOverdueCount).append("项）：\n");
            if (oldestOverdueTitle != null) {
                sb.append("   「").append(oldestOverdueTitle).append("」")
                        .append(" 已逾期 ").append(oldestOverdueDays).append(" 天，请尽快处理\n");
            }
        } else if (overdueCount > 0) {
            sb.append("⚠️ 已逾期：").append(overdueCount).append(" 项\n");
        }
        if (todayCount > 0) {
            sb.append("📅 今日截止：").append(todayCount).append(" 项");
            if (todaySampleTitle != null) {
                sb.append("（如「").append(todaySampleTitle).append("」）");
            }
            sb.append('\n');
        }
        sb.append("回复「查待办」查看详情");
        return sb.toString();
    }
}
