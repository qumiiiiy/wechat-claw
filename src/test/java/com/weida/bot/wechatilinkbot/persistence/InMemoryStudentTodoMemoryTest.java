package com.weida.bot.wechatilinkbot.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.capability.StudentTodoCapability;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import com.weida.bot.wechatilinkbot.model.TodoUrgency;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryStudentTodoMemoryTest {

    private static final String SESSION_A = "wechat-ilink|userA";
    private static final String SESSION_B = "wechat-ilink|userB";

    private final InMemoryStudentTodoMemory memory = new InMemoryStudentTodoMemory();

    // ── 1. Add then query ──────────────────────────────────────────

    @Test
    void addsTodoAndListsPending() {
        StudentTodo todo = pendingTodo(SESSION_A, "完成数学作业",
                Instant.parse("2026-07-31T12:00:00Z"));

        memory.add(todo);

        assertThat(memory.listPending(SESSION_A))
                .hasSize(1)
                .first()
                .returns(todo.id(), StudentTodo::id)
                .returns("完成数学作业", StudentTodo::title)
                .returns(StudentTodoStatus.PENDING, StudentTodo::status);
    }

    // ── 2. Add → complete → re-query ────────────────────────────

    @Test
    void completeRemovesFromPendingList() {
        StudentTodo todo = pendingTodo(SESSION_A, "英语阅读",
                Instant.parse("2026-07-31T10:00:00Z"));
        memory.add(todo);

        memory.complete(todo.id(), SESSION_A);

        assertThat(memory.listPending(SESSION_A)).isEmpty();
    }

    // ── 3. Multiple todos in same session, sorted by dueTime ─────

    @Test
    void listPendingReturnsTodosSortedByDueTime() {
        StudentTodo third = pendingTodo(SESSION_A, "第三项",
                Instant.parse("2026-08-05T10:00:00Z"));
        StudentTodo first = pendingTodo(SESSION_A, "第一项",
                Instant.parse("2026-07-31T09:00:00Z"));
        StudentTodo second = pendingTodo(SESSION_A, "第二项",
                Instant.parse("2026-08-01T14:00:00Z"));

        memory.add(first);
        memory.add(second);
        memory.add(third);

        assertThat(memory.listPending(SESSION_A))
                .extracting(StudentTodo::title)
                .containsExactly("第一项", "第二项", "第三项");
    }

    // ── 4 & 5. Session isolation ──────────────────────────────────

    @Test
    void sessionIsolationOtherSessionCannotSeeData() {
        StudentTodo todoA = pendingTodo(SESSION_A, "A 的待办",
                Instant.parse("2026-08-01T10:00:00Z"));
        StudentTodo todoB = pendingTodo(SESSION_B, "B 的待办",
                Instant.parse("2026-08-01T10:00:00Z"));

        memory.add(todoA);
        memory.add(todoB);

        assertThat(memory.listPending(SESSION_A))
                .extracting(StudentTodo::title)
                .containsExactly("A 的待办");
        assertThat(memory.listPending(SESSION_B))
                .extracting(StudentTodo::title)
                .containsExactly("B 的待办");
        assertThat(memory.listPending("wechat-ilink|unknown"))
                .isEmpty();
    }

    @Test
    void sessionIsolationCompletionIsIndependent() {
        StudentTodo todoA = pendingTodo(SESSION_A, "A 任务",
                Instant.parse("2026-08-01T10:00:00Z"));
        StudentTodo todoB = pendingTodo(SESSION_B, "B 任务",
                Instant.parse("2026-08-01T10:00:00Z"));
        memory.add(todoA);
        memory.add(todoB);

        memory.complete(todoA.id(), SESSION_A);

        assertThat(memory.listPending(SESSION_A)).isEmpty();
        assertThat(memory.listPending(SESSION_B)).hasSize(1);
    }

    @Test
    void sessionIsolationCannotCompleteAcrossSessions() {
        StudentTodo todoA = pendingTodo(SESSION_A, "A 的待办",
                Instant.parse("2026-08-01T10:00:00Z"));
        memory.add(todoA);

        assertThatThrownBy(() -> memory.complete(todoA.id(), SESSION_B))
                .isInstanceOf(StudentTodoPersistenceException.class)
                .hasMessageContaining("不存在");
    }

    // ── 6. null dueTime ────────────────────────────────────────────

    @Test
    void rejectsNullDueTime() {
        assertThatThrownBy(() -> new StudentTodo(
                todoId(), SESSION_A, "标题", null,
                StudentTodoStatus.PENDING, "manual", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("截止时间");
    }

    // ── 7 & 8. Invalid title ──────────────────────────────────────

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new StudentTodo(
                todoId(), SESSION_A, "  ",
                Instant.parse("2026-08-01T10:00:00Z"),
                StudentTodoStatus.PENDING, "manual", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("标题");
    }

    @Test
    void rejectsOversizedTitle() {
        String longTitle = "x".repeat(201);

        assertThatThrownBy(() -> new StudentTodo(
                todoId(), SESSION_A, longTitle,
                Instant.parse("2026-08-01T10:00:00Z"),
                StudentTodoStatus.PENDING, "manual", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }

    // ── 9. blank conversationId ───────────────────────────────────

    @Test
    void rejectsBlankConversationId() {
        assertThatThrownBy(() -> new StudentTodo(
                todoId(), "  ", "标题",
                Instant.parse("2026-08-01T10:00:00Z"),
                StudentTodoStatus.PENDING, "manual", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("会话");
    }

    // ── 10. Complete non-existent ID ──────────────────────────────

    @Test
    void completingNonExistentTodoThrows() {
        assertThatThrownBy(() -> memory.complete("TD-not-exist", SESSION_A))
                .isInstanceOf(StudentTodoPersistenceException.class)
                .hasMessageContaining("不存在");
    }

    // ── 11. Duplicate completion ──────────────────────────────────

    @Test
    void completingAlreadyCompletedTodoThrows() {
        StudentTodo todo = pendingTodo(SESSION_A, "任务",
                Instant.parse("2026-08-01T10:00:00Z"));
        memory.add(todo);
        memory.complete(todo.id(), SESSION_A);

        assertThatThrownBy(() -> memory.complete(todo.id(), SESSION_A))
                .isInstanceOf(StudentTodoPersistenceException.class)
                .hasMessageContaining("已完成");
    }

    // ── 12–15. TodoUrgency.classify ───────────────────────────────

    @Test
    void classifyOverdue() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-07-31T10:00:00Z"),
                ZoneOffset.UTC);
        StudentTodo todo = pendingTodo(SESSION_A, "过期任务",
                Instant.parse("2026-07-30T10:00:00Z"));

        assertThat(TodoUrgency.classify(todo, fixedClock))
                .isEqualTo(TodoUrgency.OVERDUE);
    }

    @Test
    void classifyToday() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-07-31T04:00:00Z"),
                ZoneOffset.UTC);
        StudentTodo todo = pendingTodo(SESSION_A, "今日任务",
                Instant.parse("2026-07-31T10:00:00Z"));

        assertThat(TodoUrgency.classify(todo, fixedClock))
                .isEqualTo(TodoUrgency.TODAY);
    }

    @Test
    void classifyUpcoming() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-07-30T10:00:00Z"),
                ZoneOffset.UTC);
        StudentTodo todo = pendingTodo(SESSION_A, "未来任务",
                Instant.parse("2026-08-05T10:00:00Z"));

        assertThat(TodoUrgency.classify(todo, fixedClock))
                .isEqualTo(TodoUrgency.UPCOMING);
    }

    @Test
    void classifyCompletedTodoReturnsNull() {
        StudentTodo todo = pendingTodo(SESSION_A, "已完成",
                Instant.parse("2026-08-01T10:00:00Z"));
        StudentTodo completed = todo.complete();

        assertThat(TodoUrgency.classify(completed, Clock.systemUTC()))
                .isNull();
    }

    // ── 16. Duplicate add throws ──────────────────────────────────

    @Test
    void addingDuplicateIdThrows() {
        StudentTodo todo = pendingTodo(SESSION_A, "任务",
                Instant.parse("2026-08-01T10:00:00Z"));
        memory.add(todo);

        assertThatThrownBy(() -> memory.add(todo))
                .isInstanceOf(StudentTodoPersistenceException.class)
                .hasMessageContaining("已存在");
    }

    // ── findById / DailySummary builder ────────────────────────────

    @Test
    void findByIdReturnsNullForUnknownSession() {
        assertThat(memory.findById("TD-any", "unknown-session")).isNull();
    }

    @Test
    void findByIdReturnsCompletedTodo() {
        StudentTodo todo = pendingTodo(SESSION_A, "任务",
                Instant.parse("2026-08-01T10:00:00Z"));
        memory.add(todo);
        memory.complete(todo.id(), SESSION_A);

        StudentTodo found = memory.findById(todo.id(), SESSION_A);
        assertThat(found).isNotNull();
        assertThat(found.status()).isEqualTo(StudentTodoStatus.COMPLETED);
    }

    @Test
    void emptySessionReturnsEmptyPendingList() {
        assertThat(memory.listPending(SESSION_A)).isEmpty();
    }

    @Test
    void emptyStringConversationIdIsRejectedByListPending() {
        assertThatThrownBy(() -> memory.listPending("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("会话");
    }

    // ── Capability: tomorrowTodos ──────────────────────────────────

    @Test
    void tomorrowTodosReturnsOnlyItemsDueTomorrow() {
        var cap = capability();
        memory.add(pendingTodo(SESSION_A, "明天任务",
                Instant.parse("2026-08-01T06:00:00Z")));   // 明天 14:00 CST
        memory.add(pendingTodo(SESSION_A, "今天任务",
                Instant.parse("2026-07-31T10:00:00Z")));   // 今天 18:00 CST
        memory.add(pendingTodo(SESSION_A, "后天任务",
                Instant.parse("2026-08-02T04:00:00Z")));   // 后天 12:00 CST

        var result = cap.tomorrowTodos(SESSION_A);
        assertThat(result).extracting(StudentTodo::title)
                .containsExactly("明天任务");
    }

    // ── Capability: thisWeekTodos ───────────────────────────────────

    @Test
    void thisWeekTodosExcludesTodayAndTomorrow() {
        var cap = capability();
        memory.add(pendingTodo(SESSION_A, "今天",
                Instant.parse("2026-07-31T01:00:00Z")));   // 今天 09:00 CST
        memory.add(pendingTodo(SESSION_A, "明天",
                Instant.parse("2026-08-01T02:00:00Z")));   // 明天 10:00 CST
        memory.add(pendingTodo(SESSION_A, "本周某天",
                Instant.parse("2026-08-02T06:00:00Z")));   // 周日(本周内)
        memory.add(pendingTodo(SESSION_A, "下周",
                Instant.parse("2026-08-03T06:00:00Z")));   // 下周一

        var result = cap.thisWeekTodos(SESSION_A);
        assertThat(result).extracting(StudentTodo::title)
                .contains("本周某天")
                .doesNotContain("今天", "明天", "下周");
    }

    // ── Capability: overdueTodos ────────────────────────────────────

    @Test
    void overdueTodosReturnsOnlyPastItems() {
        var cap = capability();
        memory.add(pendingTodo(SESSION_A, "未来",
                Instant.parse("2026-08-01T04:00:00Z")));   // 明天
        memory.add(pendingTodo(SESSION_A, "过期",
                Instant.parse("2026-07-28T02:00:00Z")));   // 3天前
        memory.add(pendingTodo(SESSION_A, "另一个未来",
                Instant.parse("2026-08-02T07:00:00Z")));   // 后天

        var result = cap.overdueTodos(SESSION_A);
        assertThat(result).extracting(StudentTodo::title)
                .containsExactly("过期");
    }

    // ── Capability: allTodos stable sort ────────────────────────────

    @Test
    void allTodosStableSortSameDueTimeById() {
        StudentTodo b = new StudentTodo("TD-bbbbbbbbbbbb", SESSION_A, "B",
                Instant.parse("2026-08-01T10:00:00Z"),
                StudentTodoStatus.PENDING, "manual", Instant.now());
        StudentTodo a = new StudentTodo("TD-aaaaaaaaaaaa", SESSION_A, "A",
                Instant.parse("2026-08-01T10:00:00Z"),
                StudentTodoStatus.PENDING, "manual", Instant.now());
        memory.add(a);
        memory.add(b);

        var cap = capability();
        var result = cap.allTodos(SESSION_A);
        assertThat(result).extracting(StudentTodo::title)
                .containsExactly("A", "B");
    }

    // ── Capability: cancel ──────────────────────────────────────────

    @Test
    void cancelRemovesFromPendingList() {
        memory.add(pendingTodo(SESSION_A, "可取消", Instant.parse("2026-08-01T10:00:00Z")));
        String id = memory.listPending(SESSION_A).get(0).id();

        memory.cancel(id, SESSION_A);

        assertThat(memory.listPending(SESSION_A)).isEmpty();
        assertThat(memory.findById(id, SESSION_A).status())
                .isEqualTo(StudentTodoStatus.CANCELED);
    }

    @Test
    void cancelCompletedTodoThrows() {
        memory.add(pendingTodo(SESSION_A, "任务", Instant.parse("2026-08-01T10:00:00Z")));
        String id = memory.listPending(SESSION_A).get(0).id();
        memory.complete(id, SESSION_A);

        assertThatThrownBy(() -> memory.cancel(id, SESSION_A))
                .isInstanceOf(StudentTodoPersistenceException.class);
    }

    @Test
    void completeCanceledTodoThrows() {
        memory.add(pendingTodo(SESSION_A, "任务", Instant.parse("2026-08-01T10:00:00Z")));
        String id = memory.listPending(SESSION_A).get(0).id();
        memory.cancel(id, SESSION_A);

        assertThatThrownBy(() -> memory.complete(id, SESSION_A))
                .isInstanceOf(StudentTodoPersistenceException.class);
    }

    // ── Capability: buildSummary ────────────────────────────────────

    @Test
    void buildSummaryOrdersByUrgency() {
        var cap = capability();
        memory.add(pendingTodo(SESSION_A, "逾期任务",
                Instant.parse("2026-07-28T02:00:00Z")));   // 3天前
        memory.add(pendingTodo(SESSION_A, "今天任务",
                Instant.parse("2026-07-31T12:00:00Z")));   // 今晚
        memory.add(pendingTodo(SESSION_A, "明天任务",
                Instant.parse("2026-08-01T06:00:00Z")));   // 明天

        var summary = cap.buildSummary(SESSION_A, 10);
        assertThat(summary.totalPending()).isEqualTo(3);
        assertThat(summary.truncated()).isEqualTo(0);
        assertThat(summary.overdue()).extracting(i -> i.title())
                .containsExactly("逾期任务");
        assertThat(summary.today()).extracting(i -> i.title())
                .containsExactly("今天任务");
        assertThat(summary.tomorrow()).extracting(i -> i.title())
                .containsExactly("明天任务");
    }

    @Test
    void buildSummaryRespectsLimitAndReportsTruncated() {
        var cap = capability();
        for (int i = 0; i < 5; i++) {
            memory.add(pendingTodo(SESSION_A, "任务" + i,
                    Instant.parse("2026-08-01T04:00:00Z")));  // 明天
        }

        var summary = cap.buildSummary(SESSION_A, 2);
        assertThat(summary.totalPending()).isEqualTo(5);
        assertThat(summary.tomorrow()).hasSize(2);
        assertThat(summary.truncated()).isEqualTo(3);
    }

    @Test
    void buildSummaryEmptySessionReturnsZero() {
        var cap = capability();
        var summary = cap.buildSummary(SESSION_A, 10);
        assertThat(summary.totalPending()).isEqualTo(0);
    }

    // ── helpers ────────────────────────────────────────────────────

    private static StudentTodo pendingTodo(
            String conversationId, String title, Instant dueTime) {
        return new StudentTodo(
                todoId(),
                conversationId,
                title,
                dueTime,
                StudentTodoStatus.PENDING,
                "manual",
                Instant.now());
    }

    private static String todoId() {
        return "TD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private StudentTodoCapability capability() {
        Clock fixed = Clock.fixed(
                Instant.parse("2026-07-30T20:00:00Z"),     // 7/31 04:00 CST
                ZoneId.of("Asia/Shanghai"));
        return new StudentTodoCapability(memory, fixed);
    }
}
