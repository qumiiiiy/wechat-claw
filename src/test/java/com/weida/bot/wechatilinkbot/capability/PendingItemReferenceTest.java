package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("自然引用解析")
class PendingItemReferenceTest {

    private static final String CONV = "wechat-ilink|test";

    private PendingItemReference reference;

    @BeforeEach
    void setUp() {
        reference = new PendingItemReference();
    }

    @Test
    @DisplayName("TD-xxx 原始 ID 直接透传（向后兼容）")
    void passthroughRawId() {
        var result = reference.resolve(CONV, "TD-a1b2c3d4e5f6");
        assertThat(result.isError()).isFalse();
        assertThat(result.internalId()).isEqualTo("TD-a1b2c3d4e5f6");
    }

    @Test
    @DisplayName("「第2项」根据序号查找")
    void resolveBySequenceNumber() {
        reference.remember(CONV, List.of(
                item(1, "ID-1", "任务A"),
                item(2, "ID-2", "任务B"),
                item(3, "ID-3", "任务C")));

        var result = reference.resolve(CONV, "第2项");
        assertThat(result.internalId()).isEqualTo("ID-2");
        assertThat(result.source()).isEqualTo(PendingItemSource.PERSONAL);
    }

    @Test
    @DisplayName("「第一个」中文数字查找")
    void resolveByChineseNumeral() {
        reference.remember(CONV, List.of(
                item(1, "ID-A", "第一号"),
                item(2, "ID-B", "第二号")));

        var result = reference.resolve(CONV, "第一个");
        assertThat(result.internalId()).isEqualTo("ID-A");
    }

    @Test
    @DisplayName("「刚才的」返回上次操作目标")
    void resolveLastOperation() {
        reference.markLastOperation(CONV, "TD-recent");

        var result = reference.resolve(CONV, "刚才的");
        assertThat(result.internalId()).isEqualTo("TD-recent");
    }

    @Test
    @DisplayName("「刚才那个」也匹配")
    void resolveLastOperationVariant() {
        reference.markLastOperation(CONV, "TD-recent-2");

        var result = reference.resolve(CONV, "刚才那个");
        assertThat(result.internalId()).isEqualTo("TD-recent-2");
    }

    @Test
    @DisplayName("序号不存在时返回错误")
    void sequenceOutOfRangeReturnsError() {
        reference.remember(CONV, List.of(item(1, "ID-1", "只有一项")));

        var result = reference.resolve(CONV, "第5项");
        assertThat(result.isError()).isTrue();
        assertThat(result.error()).contains("第5项不存在");
    }

    @Test
    @DisplayName("未查过列表时引用序号返回错误")
    void noSnapshotReturnsError() {
        var result = reference.resolve(CONV, "第1项");
        assertThat(result.isError()).isTrue();
        assertThat(result.error()).contains("查询待办列表");
    }

    @Test
    @DisplayName("未操作过时「刚才的」返回错误")
    void noLastOperationReturnsError() {
        var result = reference.resolve(CONV, "刚才的");
        assertThat(result.isError()).isTrue();
        assertThat(result.error()).contains("没有最近操作的待办");
    }

    @Test
    @DisplayName("中文数字「第二项」解析")
    void parseChineseNumeralSequence() {
        assertThat(PendingItemReference.parseSequence("第二项")).isEqualTo(2);
        assertThat(PendingItemReference.parseSequence("第一个")).isEqualTo(1);
        assertThat(PendingItemReference.parseSequence("第3项")).isEqualTo(3);
        assertThat(PendingItemReference.parseSequence("第 5 个")).isEqualTo(5);
        assertThat(PendingItemReference.parseSequence("模糊文本")).isEqualTo(-1);
    }

    @Test
    @DisplayName("remember 后 resolve 能查到来源")
    void sourceOfReturnsCorrectSource() {
        reference.remember(CONV, List.of(
                item(1, "ID-P", "个人项", PendingItemSource.PERSONAL),
                item(2, "ID-E", "邮件项", PendingItemSource.EMAIL)));

        assertThat(reference.sourceOf(CONV, "ID-P")).isEqualTo(PendingItemSource.PERSONAL);
        assertThat(reference.sourceOf(CONV, "ID-E")).isEqualTo(PendingItemSource.EMAIL);
        assertThat(reference.sourceOf(CONV, "ID-unknown")).isNull();
    }

    // ── helpers ──────────────────────────────────────────────────

    private static PendingItem item(int seq, String id, String title) {
        return item(seq, id, title, PendingItemSource.PERSONAL);
    }

    private static PendingItem item(int seq, String id, String title, PendingItemSource src) {
        return new PendingItem(seq, id, title, src,
                StudentTodoStatus.PENDING, Instant.now(), null);
    }
}