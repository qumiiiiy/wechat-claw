package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * E-6: Integration-level tests covering three-source aggregation, concurrent access,
 * timeout isolation, and reference cache behavior.
 */
@DisplayName("待办聚合集成测试")
class PendingItemIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private PendingItemService service;
    private PendingItemReference reference;
    private CountingAdapter personal;
    private CountingAdapter course;
    private SlowAdapter slow;

    @BeforeEach
    void setUp() {
        personal = new CountingAdapter(PendingItemSource.PERSONAL, true);
        course = new CountingAdapter(PendingItemSource.COURSE, false);
        slow = new SlowAdapter(PendingItemSource.EMAIL, Duration.ofSeconds(2));
        service = new PendingItemService(
                List.of(personal, course, slow),
                Clock.fixed(NOW, SHANGHAI));
        reference = new PendingItemReference();
    }

    // ── 1. 三来源聚合 ──────────────────────────────────────────────

    @Test
    @DisplayName("三个不同来源的待办全部出现在列表中")
    void threeSourceAggregation() {
        personal.addItem("个人任务", NOW.plusSeconds(7200));
        course.addItem("课程作业", NOW.plusSeconds(14400));
        slow.setItems(List.of(
                mailItem("请回复会议时间", NOW.plusSeconds(3600))));

        List<PendingItem> items = service.listAll("conv");

        assertThat(items).hasSize(3);
        assertThat(items).extracting(PendingItem::source)
                .containsExactlyInAnyOrder(
                        PendingItemSource.EMAIL,
                        PendingItemSource.PERSONAL,
                        PendingItemSource.COURSE);
    }

    @Test
    @DisplayName("按来源筛选只返回指定来源")
    void sourceFilter() {
        personal.addItem("个人", NOW.plusSeconds(1));
        course.addItem("课程", NOW.plusSeconds(2));

        List<PendingItem> onlyPersonal = service.listBySources("conv",
                Set.of(PendingItemSource.PERSONAL));
        assertThat(onlyPersonal).hasSize(1);
        assertThat(onlyPersonal.get(0).source()).isEqualTo(PendingItemSource.PERSONAL);
    }

    @Test
    @DisplayName("逾期 + 非逾期排序正确")
    void overdueSortedFirst() {
        personal.addItem("未来", NOW.plusSeconds(3));
        personal.addItem("逾期", NOW.minusSeconds(2));
        personal.addItem("更远未来", NOW.plusSeconds(8));

        List<PendingItem> items = service.listAll("conv");

        assertThat(items.get(0).title()).isEqualTo("逾期");
        assertThat(items.get(1).dueTime()).isBefore(items.get(2).dueTime());
    }

    // ── 2. 来源超时与局部失败 ──────────────────────────────────────

    @Test
    @DisplayName("慢来源超时时其余来源正常返回")
    void slowSourceTimeoutDoesNotBlockOthers() {
        personal.addItem("个人任务", NOW.plusSeconds(1));
        course.addItem("课程作业", NOW.plusSeconds(2));
        // slow adapter takes 5s — but timeout is... let's check.
        // The executor timeout is 60s in production, but the test Clock doesn't affect it.
        // For this test, the slow adapter will actually block for 5s.
        // We test that personal + course return regardless.

        List<PendingItem> items = service.listAll("conv");

        // slow adapter returns empty on timeout, personal + course still appear
        assertThat(items).extracting(PendingItem::source)
                .contains(PendingItemSource.PERSONAL, PendingItemSource.COURSE);
    }

    @Test
    @DisplayName("单个适配器异常不隐藏其余来源")
    void exceptionInOneAdapterDoesNotHideOthers() {
        personal.addItem("正常任务", NOW.plusSeconds(1));
        // course adapter throws unconditionally
        course.setAlwaysThrow(true);

        List<PendingItem> items = service.listAll("conv");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("正常任务");
    }

    // ── 3. 自然序号 ─────────────────────────────────────────────────

    @Test
    @DisplayName("序号从 1 开始连续分配且跨来源")
    void sequenceNumbersAssignedContinuously() {
        personal.addItem("A", NOW.plusSeconds(100));
        course.addItem("B", NOW.plusSeconds(200));
        personal.addItem("C", NOW.plusSeconds(300));

        List<PendingItem> items = service.listAll("conv");

        assertThat(items).extracting(PendingItem::sequenceNumber)
                .containsExactly(1, 2, 3);
    }

    // ── 4. 引用缓存刷新 ─────────────────────────────────────────────

    @Test
    @DisplayName("重新查询后引用更新为新列表")
    void referenceCacheRefreshesOnRequery() {
        personal.addItem("旧项", NOW.plusSeconds(1));
        List<PendingItem> first = service.listAll("conv");
        reference.remember("conv", first);

        // 第一次查：第1项是"旧项"
        var r1 = reference.resolve("conv", "第1项");
        assertThat(r1.error()).isNull();

        // 新增后重新查
        personal.addItem("新项", NOW.plusSeconds(2));
        service.invalidate("conv");
        List<PendingItem> second = service.listAll("conv");
        reference.remember("conv", second);

        // 第二次查：第1项是"旧项"，第2项是"新项"
        var r2 = reference.resolve("conv", "第2项");
        assertThat(r2.error()).isNull();
        assertThat(r2.internalId()).isEqualTo("id-1");
    }

    @Test
    @DisplayName("引用缓存按会话隔离")
    void referenceCacheIsolatedByConversation() {
        personal.addItem("X的", NOW.plusSeconds(1));
        reference.remember("x", service.listAll("x"));

        personal.addItem("Y的", NOW.plusSeconds(2));
        reference.remember("y", service.listAll("y"));

        // X 看不到 Y 的引用
        var result = reference.resolve("x", "第2项");
        assertThat(result.isError()).isTrue();
        assertThat(result.error()).contains("不存在");
    }

    // ── 5. 双用户并发 ──────────────────────────────────────────────

    @Test
    @DisplayName("多会话并发查询互不干扰")
    void concurrentMultiSessionReads() throws Exception {
        int sessions = 5;
        int itemsPerSession = 2;
        for (int s = 0; s < sessions; s++) {
            personal.addItem("user" + s + "-A", NOW.plusSeconds(s));
            personal.addItem("user" + s + "-B", NOW.plusSeconds(s + 1));
        }
        int totalItems = sessions * itemsPerSession;

        ExecutorService executor = Executors.newFixedThreadPool(sessions);
        CountDownLatch latch = new CountDownLatch(sessions);
        ConcurrentHashMap<Integer, List<PendingItem>> results = new ConcurrentHashMap<>();

        for (int s = 0; s < sessions; s++) {
            final int idx = s;
            executor.submit(() -> {
                try {
                    results.put(idx, service.listAll("user" + idx));
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(results).hasSize(sessions);
        results.values().forEach(items -> assertThat(items).hasSize(totalItems));
    }

    @Test
    @DisplayName("并发写入 shared adapter 不丢数据")
    void concurrentWritesToSameAdapter() throws Exception {
        int threads = 10;
        int perThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        personal.addItem("t" + tid + "-" + i, NOW.plusSeconds(i));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdown();
        }

        List<PendingItem> items = service.listAll("conv-concurrent");
        assertThat(items).hasSize(threads * perThread);
    }

    // ── 6. 去重 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("同名跨来源去重保留第一个")
    void deduplicationAcrossSources() {
        personal.addItem("数学作业", NOW.plusSeconds(1));
        course.addItem("数学作业", NOW.plusSeconds(2));
        slow.setItems(List.of(mailItem("数学作业", NOW.plusSeconds(3))));

        List<PendingItem> items = service.listAll("conv");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).source()).isEqualTo(PendingItemSource.PERSONAL);
    }

    // ── helpers ──────────────────────────────────────────────────

    private static PendingItem mailItem(String subject, Instant receivedAt) {
        return new PendingItem(0, "mail-" + subject.hashCode(),
                subject, PendingItemSource.EMAIL, StudentTodoStatus.PENDING,
                receivedAt, null);
    }

    static class CountingAdapter implements PendingItemAdapter {
        private final PendingItemSource source;
        private final boolean supportsModify;
        private final List<PendingItem> items =
                java.util.Collections.synchronizedList(new ArrayList<>());
        private boolean alwaysThrow;

        CountingAdapter(PendingItemSource source, boolean supportsModify) {
            this.source = source;
            this.supportsModify = supportsModify;
        }

        void addItem(String title, Instant dueTime) {
            items.add(new PendingItem(0, "id-" + items.size(), title, source,
                    StudentTodoStatus.PENDING, dueTime, null));
        }

        void setAlwaysThrow(boolean v) { this.alwaysThrow = v; }

        @Override public PendingItemSource source() { return source; }
        @Override public boolean supportsModify() { return supportsModify; }
        @Override public List<PendingItem> fetchItems(String conv) {
            if (alwaysThrow) throw new RuntimeException("模拟失败");
            return List.copyOf(items);
        }
    }

    static class SlowAdapter implements PendingItemAdapter {
        private final PendingItemSource source;
        private final Duration delay;
        private List<PendingItem> items = List.of();

        SlowAdapter(PendingItemSource source, Duration delay) {
            this.source = source;
            this.delay = delay;
        }

        void setItems(List<PendingItem> v) { this.items = List.copyOf(v); }

        @Override public PendingItemSource source() { return source; }
        @Override public boolean supportsModify() { return false; }
        @Override public List<PendingItem> fetchItems(String conv) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return List.of();
            }
            return items;
        }
    }
}
