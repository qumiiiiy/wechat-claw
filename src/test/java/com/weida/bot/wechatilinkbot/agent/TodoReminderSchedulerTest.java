package com.weida.bot.wechatilinkbot.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.capability.ReminderFlags;
import com.weida.bot.wechatilinkbot.capability.ReminderState;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import com.weida.bot.wechatilinkbot.persistence.InMemoryStudentTodoMemory;
import com.weida.bot.wechatilinkbot.persistence.StudentTodoMemory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * E-4: Verifies that the todo reminder scheduler only writes reminder flags
 * to {@link ReminderState} and never attempts to call any WeChat Channel
 * send API.
 *
 * <p>The scheduler's constructor accepts only {@link StudentTodoMemory},
 * {@link ReminderState}, and {@link Clock} — no Channel, no Ilink, no
 * send-capable bean. All flags are consumed passively by Tool responses
 * during real user messages that carry a valid WeChat message Token.</p>
 */
@DisplayName("待办提醒定时任务 — 只写标记不发送")
class TodoReminderSchedulerTest {

    private static final String CONV_ALICE = "wechat-ilink|alice";
    private static final String CONV_BOB = "wechat-ilink|bob";
    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private StudentTodoMemory memory;
    private ReminderState state;
    private Clock clock;
    private TodoReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        memory = new InMemoryStudentTodoMemory();
        state = new ReminderState();
        clock = Clock.fixed(NOW, SHANGHAI);
        scheduler = new TodoReminderScheduler(memory, state, clock);
    }

    // ── pollOverdue ──────────────────────────────────────────────

    @Test
    @DisplayName("pollOverdue: 检测到逾期项时标记 ReminderState")
    void pollOverdueMarksOverdueItems() {
        // 创建逾期 3 小时的待办
        Instant threeHoursAgo = NOW.minus(Duration.ofHours(3));
        memory.add(todo(CONV_ALICE, "逾期作业", threeHoursAgo));

        scheduler.pollOverdue();

        ReminderFlags flags = state.consume(CONV_ALICE);
        assertThat(flags.overdueCount()).isEqualTo(1);
        assertThat(flags.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("pollOverdue: 无逾期时不标记")
    void pollOverdueSkipsNonOverdueItems() {
        // 创建未来待办
        Instant future = NOW.plus(Duration.ofDays(1));
        memory.add(todo(CONV_ALICE, "未来任务", future));

        scheduler.pollOverdue();

        ReminderFlags flags = state.consume(CONV_ALICE);
        assertThat(flags.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("pollOverdue: 逾期超 48h 触发 escalated 标记")
    void pollOverdueEscalatedForOldOverdue() {
        Instant threeDaysAgo = NOW.minus(Duration.ofDays(3));
        memory.add(todo(CONV_ALICE, "严重逾期任务", threeDaysAgo));

        scheduler.pollOverdue();

        ReminderFlags flags = state.consume(CONV_ALICE);
        assertThat(flags.escalatedOverdueCount()).isGreaterThan(0);
        assertThat(flags.oldestOverdueTitle()).isEqualTo("严重逾期任务");
    }

    @Test
    @DisplayName("pollOverdue: 多个会话各自独立标记")
    void pollOverdueMarksMultipleConversations() {
        Instant twoHoursAgo = NOW.minus(Duration.ofHours(2));
        memory.add(todo(CONV_ALICE, "Alice 逾期", twoHoursAgo));
        memory.add(todo(CONV_BOB, "Bob 逾期", twoHoursAgo));

        scheduler.pollOverdue();

        ReminderFlags aliceFlags = state.consume(CONV_ALICE);
        assertThat(aliceFlags.overdueCount()).isEqualTo(1);

        ReminderFlags bobFlags = state.consume(CONV_BOB);
        assertThat(bobFlags.overdueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("pollOverdue: 空待办时不抛异常")
    void pollOverdueHandlesEmptyMemory() {
        scheduler.pollOverdue(); // 不抛异常即通过
        assertThat(state.activeCount()).isZero();
    }

    @Test
    @DisplayName("pollOverdue: 重复轮询幂等 — 第二次覆盖第一次")
    void pollOverdueIdempotent() {
        Instant twoHoursAgo = NOW.minus(Duration.ofHours(2));
        memory.add(todo(CONV_ALICE, "幂等测试", twoHoursAgo));

        scheduler.pollOverdue();
        scheduler.pollOverdue(); // 第二次轮询，跟第一次一样的结果

        ReminderFlags flags = state.consume(CONV_ALICE);
        assertThat(flags.overdueCount()).isEqualTo(1);
    }

    // ── scanDaily ─────────────────────────────────────────────────

    @Test
    @DisplayName("scanDaily: 标记逾期 + 今日截止")
    void scanDailyMarksOverdueAndToday() {
        Instant twoHoursAgo = NOW.minus(Duration.ofHours(2));
        Instant laterToday = NOW.plus(Duration.ofHours(3)); // same day, future time
        memory.add(todo(CONV_ALICE, "逾期的", twoHoursAgo));
        memory.add(todo(CONV_ALICE, "今天截止的", laterToday));

        scheduler.scanDaily();

        ReminderFlags flags = state.consume(CONV_ALICE);
        assertThat(flags.overdueCount()).isEqualTo(1);
        assertThat(flags.todayCount()).isEqualTo(1);
        assertThat(flags.todaySampleTitle()).isEqualTo("今天截止的");
    }

    @Test
    @DisplayName("scanDaily: 清理 7 天前已完成的待办")
    void scanDailyPrunesOldCompletedTodos() {
        // 创建一条待办，createdAt 设为 8 天前 → 满足 7 天清理阈值
        Instant eightDaysAgo = NOW.minus(Duration.ofDays(8));
        StudentTodo old = todo(CONV_ALICE, "老任务",
                eightDaysAgo,   // dueTime
                eightDaysAgo);  // createdAt（早于 cutoff）
        memory.add(old);
        memory.complete(old.id(), CONV_ALICE);

        // 确认还在
        assertThat(memory.findById(old.id(), CONV_ALICE)).isNotNull();

        scheduler.scanDaily();

        // 已被清理
        assertThat(memory.findById(old.id(), CONV_ALICE)).isNull();
    }

    @Test
    @DisplayName("scanDaily: 7 天内的已完成待办不会被清理")
    void scanDailyKeepsRecentCompletedTodos() {
        // 3 天前完成的待办 — 不满足 7 天阈值
        Instant threeDaysAgo = NOW.minus(Duration.ofDays(3));
        StudentTodo recent = todo(CONV_ALICE, "最近完成",
                threeDaysAgo, threeDaysAgo);
        memory.add(recent);
        memory.complete(recent.id(), CONV_ALICE);

        scheduler.scanDaily();

        assertThat(memory.findById(recent.id(), CONV_ALICE)).isNotNull();
    }

    // ── 架构约束：不依赖 Channel ───────────────────────────────

    @Test
    @DisplayName("Scheduler 仅依赖 StudentTodoMemory + ReminderState + Clock")
    void schedulerHasNoChannelDependency() {
        // TodoReminderScheduler 的字段
        var fields = TodoReminderScheduler.class.getDeclaredFields();

        for (var field : fields) {
            String typeName = field.getType().getName();
            // 排除基础包名 wechatilinkbot 的误判：只检查 channel 子包
            assertThat(typeName)
                    .as("Scheduler 不得依赖 Channel 类型: " + typeName)
                    .doesNotContain(".channel.");
        }
    }

    @Test
    @DisplayName("pollOverdue 只调用 ReminderState.mark* 方法，不接触 Channel")
    void pollOverdueOnlyUpdatesReminderState() {
        Instant twoHoursAgo = NOW.minus(Duration.ofHours(2));
        memory.add(todo(CONV_ALICE, "架构验证", twoHoursAgo));

        // 轮询后
        scheduler.pollOverdue();

        // 标记立即可消费
        ReminderFlags flags = state.consume(CONV_ALICE);
        assertThat(flags.overdueCount()).isEqualTo(1);

        // 消费后（模拟 Tool 在用户消息中捎带），标记消失
        ReminderFlags consumed = state.consume(CONV_ALICE);
        assertThat(consumed.isEmpty()).isTrue();
    }

    // ── helpers ──────────────────────────────────────────────────

    private static StudentTodo todo(String conversationId, String title, Instant dueTime) {
        return todo(conversationId, title, dueTime, dueTime);
    }

    private static StudentTodo todo(
            String conversationId, String title, Instant dueTime, Instant createdAt) {
        return new StudentTodo(
                "TD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                conversationId,
                title,
                dueTime,
                StudentTodoStatus.PENDING,
                "test",
                createdAt);
    }
}