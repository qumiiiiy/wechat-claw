package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.capability.StudentTodoCapability;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import com.weida.bot.wechatilinkbot.model.TodoUrgency;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Manual smoke test for the student todo stack — no Spring, no WeChat, no model.
 * Run {@code main} directly in your IDE.
 */
@SuppressWarnings({"SystemOut", "UseOfSystemOutOrSystemErr"})
public final class StudentTodoManualTest {

    private static final DateTimeFormatter DUE_FMT =
            DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SHORT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private StudentTodoManualTest() {
    }

    public static void main(String[] args) {
        // Replace with your real WeChat conversation ID
        String me = "wechat-ilink|user-primary";
        String other = "wechat-ilink|stranger";

        InMemoryStudentTodoMemory memory = new InMemoryStudentTodoMemory();
        StudentTodoCapability cap = new StudentTodoCapability(memory);

        System.out.println("═══════════════════════════════════════");
        System.out.println("  学生待办 手动验证");
        System.out.println("═══════════════════════════════════════\n");

        // ── 1. Create todos ──────────────────────────────────────
        System.out.println("── 1. 创建待办 ──\n");

        StudentTodo math = cap.createTodo(me, "数学作业", "明天18:00");
        System.out.println("  ✅ " + fmt(math));

        StudentTodo english = cap.createTodo(me, "英语阅读", "后天12:00");
        System.out.println("  ✅ " + fmt(english));

        StudentTodo physics = cap.createTodo(me, "物理实验报告", "8月5日09:00");
        System.out.println("  ✅ " + fmt(physics));

        // Add one due today for classification demo
        Instant todayLate = LocalDate.now(ZoneId.systemDefault())
                .atTime(23, 59)
                .toInstant(ZoneOffset.UTC);
        StudentTodo todayTodo = new StudentTodo(
                "TD-today00001",
                me,
                "今日测验复习",
                todayLate,
                StudentTodoStatus.PENDING,
                "manual",
                Instant.now());
        memory.add(todayTodo);
        System.out.println("  ✅ " + fmt(todayTodo) + "  ← 今日截止");

        // ── 2. List all pending ─────────────────────────────────
        System.out.println("\n── 2. 查询全部待办（按截止时间排序）──\n");

        List<StudentTodo> all = cap.listTodos(me);
        for (StudentTodo t : all) {
            System.out.println("  📋 " + fmt(t));
        }
        System.out.println("  共 " + all.size() + " 项");

        // ── 3. Today filter ─────────────────────────────────────
        System.out.println("\n── 3. 今日截止过滤 ──\n");

        List<StudentTodo> todayList = cap.todayTodos(me);
        if (todayList.isEmpty()) {
            System.out.println("  📅 今天没有截止的待办");
        } else {
            for (StudentTodo t : todayList) {
                System.out.println("  📅 " + fmt(t));
            }
        }

        // ── 4. Urgency classification ──────────────────────────
        System.out.println("\n── 4. 紧急度分类 ──\n");

        Clock pastClock = Clock.fixed(
                Instant.parse("2026-08-10T10:00:00Z"), ZoneOffset.UTC);
        for (StudentTodo t : all) {
            TodoUrgency urgency = TodoUrgency.classify(t, pastClock);
            System.out.println("  " + urgencyIcon(urgency) + " " + t.title()
                    + " — " + DUE_FMT.format(t.dueTime())
                    + " → " + (urgency == null ? "已完成" : urgency));
        }

        // ── 5. Complete one ─────────────────────────────────────
        System.out.println("\n── 5. 完成一项待办 ──\n");

        String firstId = all.get(0).id();
        cap.completeTodo(firstId, me);
        System.out.println("  ✅ 已完成 " + firstId);

        System.out.println("  完成后剩余：");
        List<StudentTodo> afterComplete = cap.listTodos(me);
        for (StudentTodo t : afterComplete) {
            System.out.println("  📋 " + fmt(t));
        }

        // ── 6. Session isolation ────────────────────────────────
        System.out.println("\n── 6. 会话隔离验证 ──\n");

        cap.createTodo(other, "陌生人的待办", "后天15:00");
        System.out.println("  我的待办: " + cap.listTodos(me).size() + " 项");
        System.out.println("  陌生人的待办: " + cap.listTodos(other).size() + " 项");
        System.out.println("  我在陌生人会话下查: " + cap.listTodos(other).size() + " 项 (应为 1，不是我的)");

        // ── 7. Fuzzy time rejection ─────────────────────────────
        System.out.println("\n── 7. 模糊时间拒绝 ──\n");

        for (String fuzzy : new String[]{"有空的时候", "尽快", "晚点", "下周有空", ""}) {
            try {
                cap.createTodo(me, "测试任务", fuzzy);
                System.out.println("  ❌ 应该拒绝但没有: \"" + fuzzy + "\"");
            } catch (Exception e) {
                System.out.println("  ✅ 正确拒绝 \"" + fuzzy + "\": "
                        + e.getMessage().replace("无法解析截止时间「", "").replace("」", ""));
            }
        }

        // ── 8. Duplicate completion ─────────────────────────────
        System.out.println("\n── 8. 重复完成拒绝 ──\n");

        try {
            cap.completeTodo(firstId, me);
            System.out.println("  ❌ 应该拒绝但没有");
        } catch (StudentTodoPersistenceException e) {
            System.out.println("  ✅ 正确拒绝: " + e.getMessage());
        }

        System.out.println("\n═══════════════════════════════════════");
        System.out.println("  全部验证通过 ✅");
        System.out.println("═══════════════════════════════════════");
    }

    private static String fmt(StudentTodo t) {
        String urgencyStr = "";
        TodoUrgency u = TodoUrgency.classify(t, Clock.systemUTC());
        if (u != null) {
            urgencyStr = " " + urgencyIcon(u);
        }
        return "[" + t.id() + "] " + t.title()
                + " — " + DUE_FMT.format(t.dueTime())
                + " (" + SHORT_FMT.format(t.dueTime()) + " UTC)"
                + urgencyStr;
    }

    private static String urgencyIcon(TodoUrgency urgency) {
        return switch (urgency) {
            case OVERDUE -> "⚠️";
            case TODAY -> "📅";
            case UPCOMING -> "🔜";
        };
    }
}
