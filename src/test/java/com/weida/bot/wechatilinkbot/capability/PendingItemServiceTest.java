package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("待办聚合服务")
class PendingItemServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private PendingItemService service;
    private StubAdapter personal;
    private StubAdapter email;
    private StubAdapter course;

    @BeforeEach
    void setUp() {
        personal = new StubAdapter(PendingItemSource.PERSONAL, true);
        email = new StubAdapter(PendingItemSource.EMAIL, false);
        course = new StubAdapter(PendingItemSource.COURSE, false);
        Clock clock = Clock.fixed(NOW, SHANGHAI);
        service = new PendingItemService(List.of(personal, email, course), clock);
    }

    @Test
    @DisplayName("三个来源全部聚合")
    void aggregatesAllSources() {
        personal.addItem("个人任务", NOW.plusSeconds(3600));
        email.addItem("发票邮件", NOW.plusSeconds(7200));
        course.addItem("数学作业", NOW.plusSeconds(10800));

        List<PendingItem> items = service.listAll("conv");

        assertThat(items).hasSize(3);
        assertThat(items).extracting(PendingItem::source)
                .contains(PendingItemSource.PERSONAL, PendingItemSource.EMAIL, PendingItemSource.COURSE);
    }

    @Test
    @DisplayName("按来源筛选：只看邮箱")
    void filtersBySource() {
        personal.addItem("个人任务", NOW.plusSeconds(3600));
        email.addItem("发票邮件", NOW.plusSeconds(7200));

        List<PendingItem> items = service.listBySources("conv",
                Set.of(PendingItemSource.EMAIL));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).source()).isEqualTo(PendingItemSource.EMAIL);
    }

    @Test
    @DisplayName("按自定义时间范围过滤截止时间")
    void filtersByCustomTimeRange() {
        personal.addItem("七月任务", Instant.parse("2026-07-20T02:00:00Z"));
        personal.addItem("八月任务", Instant.parse("2026-08-20T02:00:00Z"));

        PendingItemService.PendingItemQuery result = service.listRangeWithStatus(
                "conv",
                Set.of(PendingItemSource.PERSONAL),
                "2026-08-01",
                "2026-09-01");

        assertThat(result.items())
                .extracting(PendingItem::title)
                .containsExactly("八月任务");
    }

    @Test
    @DisplayName("无效时间范围给出明确错误")
    void rejectsInvalidTimeRange() {
        assertThatThrownBy(() -> service.listRangeWithStatus(
                "conv",
                Set.of(PendingItemSource.PERSONAL),
                "不是日期",
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法解析待办时间范围");
    }

    @Test
    @DisplayName("默认待办只返回一个月内（含逾期），无截止时间不参与")
    void defaultUpcomingWindowCoversOverdueAndNextThirtyDaysOnly() {
        personal.addItem("逾期任务", NOW.minusSeconds(3600));
        personal.addItem("两周后任务", NOW.plus(Duration.ofDays(14)));
        personal.addItem("四十天后任务", NOW.plus(Duration.ofDays(40)));
        personal.addItem("无截止任务", Instant.MAX);

        List<PendingItem> items = service.listUpcomingWithStatus(
                "conv",
                Set.of(PendingItemSource.PERSONAL))
                .items();

        assertThat(items).extracting(PendingItem::title)
                .containsExactly("逾期任务", "两周后任务");
    }

    @Test
    @DisplayName("无截止时间格式化不崩溃")
    void noDeadlineItemFormatsWithoutCrash() {
        PendingItem item = new PendingItem(
                1,
                "id-no-deadline",
                "无截止任务",
                PendingItemSource.COURSE,
                StudentTodoStatus.PENDING,
                Instant.MAX,
                null);

        assertThat(item.formatListItem()).contains("无截止时间");
    }

    @Test
    @DisplayName("去重：相同标题只保留第一个")
    void deduplicatesByTitle() {
        personal.addItem("数学作业", NOW.plusSeconds(3600));
        course.addItem("数学作业", NOW.plusSeconds(7200));

        List<PendingItem> items = service.listAll("conv");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).source()).isEqualTo(PendingItemSource.PERSONAL); // first wins
    }

    @Test
    @DisplayName("逾期排最前面")
    void overdueSortedFirst() {
        personal.addItem("未来任务", NOW.plusSeconds(3600));
        personal.addItem("逾期任务", NOW.minusSeconds(3600));

        List<PendingItem> items = service.listAll("conv");

        assertThat(items).extracting(PendingItem::title)
                .containsExactly("逾期任务", "未来任务");
    }

    @Test
    @DisplayName("序号从 1 开始连续分配")
    void sequenceNumbersAssigned() {
        personal.addItem("A", NOW.plusSeconds(100));
        personal.addItem("B", NOW.plusSeconds(200));
        personal.addItem("C", NOW.plusSeconds(300));

        List<PendingItem> items = service.listAll("conv");

        assertThat(items).extracting(PendingItem::sequenceNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("单来源异常不影响其余来源")
    void singleSourceFailureDoesNotHideOthers() {
        personal.addItem("正常任务", NOW.plusSeconds(3600));
        email.setThrowOnFetch(true); // 邮箱挂了

        List<PendingItem> items = service.listAll("conv");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("正常任务");
    }

    @Test
    @DisplayName("来源失败不会伪装成空列表，并返回可操作告警")
    void sourceFailureIsReportedAlongsideRemainingItems() {
        personal.addItem("正常任务", NOW.plusSeconds(3600));
        course.setSourceFailure("尚未登录学习通，请先扫码登录。");

        PendingItemService.PendingItemQuery result = service.listAllWithStatus("conv");

        assertThat(result.items()).extracting(PendingItem::title)
                .containsExactly("正常任务");
        assertThat(result.warnings()).containsExactly(
                "学习通待办暂时无法查询：尚未登录学习通，请先扫码登录。");
    }

    @Test
    @DisplayName("空来源返回空列表")
    void emptySourcesReturnsEmpty() {
        List<PendingItem> items = service.listAll("conv");
        assertThat(items).isEmpty();
    }

    // ── stub adapter ─────────────────────────────────────────────

    static class StubAdapter implements PendingItemAdapter {
        private final PendingItemSource source;
        private final boolean supportsModify;
        private final List<PendingItem> items = new java.util.ArrayList<>();
        private boolean throwOnFetch;
        private String sourceFailure;

        StubAdapter(PendingItemSource source, boolean supportsModify) {
            this.source = source;
            this.supportsModify = supportsModify;
        }

        void addItem(String title, Instant dueTime) {
            items.add(new PendingItem(0, "id-" + items.size(), title, source,
                    StudentTodoStatus.PENDING, dueTime, null));
        }

        void setThrowOnFetch(boolean v) { this.throwOnFetch = v; }

        void setSourceFailure(String message) { this.sourceFailure = message; }

        @Override public PendingItemSource source() { return source; }
        @Override public boolean supportsModify() { return supportsModify; }
        @Override public List<PendingItem> fetchItems(String conversationId) {
            if (sourceFailure != null) {
                throw new PendingItemSourceException(sourceFailure);
            }
            if (throwOnFetch) throw new RuntimeException("模拟失败");
            return List.copyOf(items);
        }
    }
}
