package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.model.DailySummary;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import com.weida.bot.wechatilinkbot.model.TodoUrgency;
import com.weida.bot.wechatilinkbot.persistence.StudentTodoMemory;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Business logic for student todo creation, query, completion, and classification. */
@Component
public class StudentTodoCapability {

    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final Pattern MONTH_DAY_TIME = Pattern.compile(
            "(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern RELATIVE_DAY_TIME = Pattern.compile(
            "(今天|明天|后天|大后天)\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern NEXT_WEEKDAY_TIME = Pattern.compile(
            "下?(?:周|星期)([一二三四五六日天])\\s*(\\d{1,2}):(\\d{2})");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M月d日");

    private final StudentTodoMemory memory;
    private final Clock clock;

    @Autowired
    public StudentTodoCapability(StudentTodoMemory memory) {
        this(memory, Clock.system(SHANGHAI));
    }

    public StudentTodoCapability(StudentTodoMemory memory, Clock clock) {
        this.memory = memory;
        this.clock = clock;
    }

    private static final Pattern FUZZY_TIME = Pattern.compile(
            "下班|有空|尽快|晚点|周末|过两天|等一会|等会儿|晚些|晚点|到时候|再说|不急|以后");

    /** Creates a new pending todo. The caller must provide a validated conversation ID. */
    public StudentTodo createTodo(String conversationId, String title, String dueTimeStr) {
        String safeConversationId = requireText(conversationId, "会话 ID");
        String safeTitle = requireText(title, "标题");
        if (safeTitle.length() > 200) {
            throw new IllegalArgumentException("标题不能超过 200 字符");
        }
        String safeDueTimeStr = requireText(dueTimeStr, "截止时间");
        if (FUZZY_TIME.matcher(safeDueTimeStr).find()
                || FUZZY_TIME.matcher(safeTitle).find()) {
            throw new IllegalArgumentException(
                    "时间「" + safeDueTimeStr + "」不够明确，请直接说几点几分，"
                            + "例如「明天18:00」「今天17:30」");
        }
        Instant dueTime = parseTime(safeDueTimeStr);
        Instant now = Instant.now(clock);
        if (dueTime.isBefore(now)) {
            throw new IllegalArgumentException(
                    "截止时间「" + dueTimeStr + "」是过去的时间，请使用未来的时间");
        }
        StudentTodo todo = new StudentTodo(
                "TD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                safeConversationId,
                safeTitle,
                dueTime,
                StudentTodoStatus.PENDING,
                "manual",
                Instant.now(clock));
        memory.add(todo);
        return todo;
    }

    /** Lists all pending todos for a conversation, sorted by due time. */
    public List<StudentTodo> listTodos(String conversationId) {
        requireConversationId(conversationId);
        return memory.listPending(conversationId);
    }

    private static final Comparator<StudentTodo> STABLE_SORT =
            Comparator.comparing(StudentTodo::dueTime).thenComparing(StudentTodo::id);

    /** Lists pending todos due today. */
    public List<StudentTodo> todayTodos(String conversationId) {
        requireConversationId(conversationId);
        return memory.listPending(conversationId).stream()
                .filter(todo -> TodoUrgency.classify(todo, clock) == TodoUrgency.TODAY)
                .sorted(STABLE_SORT)
                .toList();
    }

    /** Lists pending todos due tomorrow. */
    public List<StudentTodo> tomorrowTodos(String conversationId) {
        requireConversationId(conversationId);
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        return memory.listPending(conversationId).stream()
                .filter(todo -> {
                    LocalDate dueDay = LocalDate.ofInstant(todo.dueTime(), clock.getZone());
                    return dueDay.isEqual(tomorrow);
                })
                .sorted(STABLE_SORT)
                .toList();
    }

    /** Lists pending todos due this week (excluding today and tomorrow). */
    public List<StudentTodo> thisWeekTodos(String conversationId) {
        requireConversationId(conversationId);
        LocalDate today = LocalDate.now(clock);
        LocalDate tomorrow = today.plusDays(1);
        // Monday of this week
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(java.time.DayOfWeek.SUNDAY);
        return memory.listPending(conversationId).stream()
                .filter(todo -> {
                    LocalDate dueDay = LocalDate.ofInstant(todo.dueTime(), clock.getZone());
                    if (dueDay.isBefore(weekStart) || dueDay.isAfter(weekEnd)) {
                        return false;
                    }
                    return !dueDay.isEqual(today) && !dueDay.isEqual(tomorrow);
                })
                .sorted(STABLE_SORT)
                .toList();
    }

    /** Lists pending todos that are overdue. */
    public List<StudentTodo> overdueTodos(String conversationId) {
        requireConversationId(conversationId);
        Instant now = Instant.now(clock);
        return memory.listPending(conversationId).stream()
                .filter(todo -> todo.dueTime().isBefore(now))
                .sorted(STABLE_SORT)
                .toList();
    }

    /** Lists pending todos due next week. */
    public List<StudentTodo> nextWeekTodos(String conversationId) {
        requireConversationId(conversationId);
        LocalDate today = LocalDate.now(clock);
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate nextSunday = nextMonday.with(DayOfWeek.SUNDAY);
        return memory.listPending(conversationId).stream()
                .filter(todo -> {
                    LocalDate dueDay = LocalDate.ofInstant(todo.dueTime(), clock.getZone());
                    return !dueDay.isBefore(nextMonday) && !dueDay.isAfter(nextSunday);
                })
                .sorted(STABLE_SORT)
                .toList();
    }

    /** Lists pending todos due this month. */
    public List<StudentTodo> thisMonthTodos(String conversationId) {
        requireConversationId(conversationId);
        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        return memory.listPending(conversationId).stream()
                .filter(todo -> {
                    LocalDate dueDay = LocalDate.ofInstant(todo.dueTime(), clock.getZone());
                    return !dueDay.isBefore(monthStart) && !dueDay.isAfter(monthEnd);
                })
                .sorted(STABLE_SORT)
                .toList();
    }

    /** Builds a daily summary with at most {@code limit} items, truncated count included. */
    public DailySummary buildSummary(String conversationId, int limit) {
        requireConversationId(conversationId);
        List<StudentTodo> all = memory.listPending(conversationId).stream()
                .sorted(STABLE_SORT)
                .toList();
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        LocalDate tomorrow = today.plusDays(1);
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate nextSunday = nextMonday.with(DayOfWeek.SUNDAY);

        var overdue = filterAndMap(all, t -> t.dueTime().isBefore(now), now, limit);
        int remaining = limit - overdue.size();
        var todayItems = filterAndMap(all,
                t -> !t.dueTime().isBefore(now)
                        && LocalDate.ofInstant(t.dueTime(), clock.getZone()).isEqual(today),
                now, Math.max(0, remaining));
        remaining = limit - overdue.size() - todayItems.size();
        var tomorrowItems = filterAndMap(all,
                t -> LocalDate.ofInstant(t.dueTime(), clock.getZone()).isEqual(tomorrow),
                now, Math.max(0, remaining));
        remaining = limit - overdue.size() - todayItems.size() - tomorrowItems.size();
        var weekItems = filterAndMap(all,
                t -> {
                    LocalDate d = LocalDate.ofInstant(t.dueTime(), clock.getZone());
                    return !d.isBefore(nextMonday) && !d.isAfter(nextSunday);
                },
                now, Math.max(0, remaining));

        int total = all.size();
        int included = overdue.size() + todayItems.size() + tomorrowItems.size() + weekItems.size();
        return new DailySummary(now, overdue, todayItems, tomorrowItems, weekItems, total,
                Math.max(0, total - included));
    }

    private List<DailySummary.TodoItem> filterAndMap(
            List<StudentTodo> source,
            java.util.function.Predicate<StudentTodo> pred,
            Instant now,
            int limit) {
        return source.stream()
                .filter(pred)
                .limit(limit)
                .map(t -> new DailySummary.TodoItem(
                        t.id(), t.title(), t.dueTime(),
                        TodoUrgency.classify(t, clock)))
                .toList();
    }

    /** Lists all pending todos with stable sort. */
    public List<StudentTodo> allTodos(String conversationId) {
        requireConversationId(conversationId);
        return memory.listPending(conversationId).stream()
                .sorted(STABLE_SORT)
                .toList();
    }

    /** Completes one todo. */
    public StudentTodo completeTodo(String id, String conversationId) {
        requireConversationId(conversationId);
        return memory.complete(id, conversationId);
    }

    /** Cancels one todo. */
    public StudentTodo cancelTodo(String id, String conversationId) {
        requireConversationId(conversationId);
        return memory.cancel(id, conversationId);
    }

    /** Changes the due time of one pending todo. */
    public StudentTodo modifyDueTime(String id, String conversationId, String newDueTimeStr) {
        String safeNewDueTimeStr = requireText(newDueTimeStr, "新截止时间");
        Instant newDueTime = parseTime(safeNewDueTimeStr);
        if (newDueTime.isBefore(Instant.now(clock))) {
            throw new IllegalArgumentException("新截止时间「" + newDueTimeStr + "」是过去的时间");
        }
        return memory.modifyDueTime(id, conversationId, newDueTime);
    }

    /** Finds a single todo by ID. */
    public StudentTodo findById(String id, String conversationId) {
        return memory.findById(id, conversationId);
    }

    // ── time parsing ────────────────────────────────────────────────

    Instant parseTime(String raw) {
        String normalized = raw.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("截止时间不能为空");
        }
        // "8月5日14:30" or "8月5号14:30"
        Matcher absolute = MONTH_DAY_TIME.matcher(normalized);
        if (absolute.matches()) {
            return parseAbsolute(
                    Integer.parseInt(absolute.group(1)),
                    Integer.parseInt(absolute.group(2)),
                    Integer.parseInt(absolute.group(3)),
                    Integer.parseInt(absolute.group(4)));
        }
        // "明天18:00" / "今天09:30" / "后天12:00"
        Matcher relative = RELATIVE_DAY_TIME.matcher(normalized);
        if (relative.matches()) {
            return parseRelative(
                    relative.group(1),
                    Integer.parseInt(relative.group(2)),
                    Integer.parseInt(relative.group(3)));
        }
        // "下周一14:00" / "周三10:00"
        Matcher weekday = NEXT_WEEKDAY_TIME.matcher(normalized);
        if (weekday.matches()) {
            return parseNextWeekday(
                    weekday.group(1),
                    normalized.startsWith("下周"),
                    Integer.parseInt(weekday.group(2)),
                    Integer.parseInt(weekday.group(3)));
        }
        // "2026-08-05T14:30:00Z" — ISO fallback
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        throw new IllegalArgumentException(
                "无法解析截止时间「" + raw + "」，请使用「明天18:00」「8月5日09:00」「下周一14:00」等明确格式");
    }

    private Instant parseAbsolute(int month, int day, int hour, int minute) {
        LocalDate today = LocalDate.now(clock);
        int year = today.getYear();
        LocalDate parsed = LocalDate.of(year, month, day);
        // If the parsed date is more than 6 months before today, assume next year
        // (e.g. December saying "1月1日" means next January)
        if (parsed.plusMonths(6).isBefore(today)) {
            year++;
            parsed = LocalDate.of(year, month, day);
        }
        return parsed.atTime(LocalTime.of(hour, minute))
                .toInstant(ZoneOffset.ofHours(8));
    }

    private Instant parseRelative(String label, int hour, int minute) {
        long offset = switch (label) {
            case "今天" -> 0;
            case "明天" -> 1;
            case "后天" -> 2;
            case "大后天" -> 3;
            default -> throw new IllegalArgumentException("不支持的相对日期：「" + label + "」");
        };
        return LocalDate.now(clock).plusDays(offset)
                .atTime(LocalTime.of(hour, minute))
                .toInstant(ZoneOffset.ofHours(8));
    }

    private Instant parseNextWeekday(String dayLabel, boolean nextWeek, int hour, int minute) {
        DayOfWeek target = switch (dayLabel) {
            case "一" -> DayOfWeek.MONDAY;
            case "二" -> DayOfWeek.TUESDAY;
            case "三" -> DayOfWeek.WEDNESDAY;
            case "四" -> DayOfWeek.THURSDAY;
            case "五" -> DayOfWeek.FRIDAY;
            case "六" -> DayOfWeek.SATURDAY;
            case "日", "天" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("不支持的星期：「" + dayLabel + "」");
        };
        LocalDate today = LocalDate.now(clock);
        LocalDate result;
        if (nextWeek) {
            result = today.with(TemporalAdjusters.next(target));
        } else {
            result = today.with(TemporalAdjusters.nextOrSame(target));
        }
        return result.atTime(LocalTime.of(hour, minute)).toInstant(ZoneOffset.ofHours(8));
    }

    // ── helpers ─────────────────────────────────────────────────────

    static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " 不能为空");
        }
        return value.strip();
    }

    static void requireConversationId(String conversationId) {
        requireText(conversationId, "会话 ID");
    }
}
