package com.weida.bot.wechatilinkbot.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationReferenceStoreTest {

    @Test
    void resolvesNaturalOrdinalOnlyWithinConversationAndType() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T00:00:00Z"));
        ConversationReferenceStore store = new ConversationReferenceStore(
                Duration.ofMinutes(30),
                20,
                clock);
        store.rememberGroup(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.FILE,
                List.of(
                        draft("file-one", "课程表.pdf"),
                        draft("file-two", "实验报告.docx")));
        store.rememberGroup(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.TODO,
                List.of(draft("TD-123456789abc", "提交实验报告")));
        store.rememberGroup(
                "wechat|b",
                ConversationReferenceStore.ReferenceType.FILE,
                List.of(draft("file-other", "另一份文件.pdf")));

        assertThat(store.resolve(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.FILE,
                "把第二个发给我")).get()
                .extracting(ConversationReferenceStore.Reference::identifier)
                .isEqualTo("file-two");
        assertThat(store.resolve(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.TODO,
                "完成第二项")).isEmpty();
        assertThat(store.resolve(
                "wechat|b",
                ConversationReferenceStore.ReferenceType.FILE,
                "把第一个发给我")).get()
                .extracting(ConversationReferenceStore.Reference::identifier)
                .isEqualTo("file-other");
    }

    @Test
    void resolvesLatestPhraseAndExactIdBeforeOrdinal() {
        ConversationReferenceStore store = new ConversationReferenceStore(
                Duration.ofMinutes(30),
                20,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));
        store.rememberGroup(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.DESKTOP_TASK,
                List.of(draft("DT-11111111", "打开课程页面")));
        store.rememberGroup(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.DESKTOP_TASK,
                List.of(draft("DT-22222222", "打开邮箱页面")));

        assertThat(store.resolve(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.DESKTOP_TASK,
                "进度怎么样")).get()
                .extracting(ConversationReferenceStore.Reference::identifier)
                .isEqualTo("DT-22222222");
        assertThat(store.resolve(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.DESKTOP_TASK,
                "查询 DT-11111111 状态")).get()
                .extracting(ConversationReferenceStore.Reference::identifier)
                .isEqualTo("DT-11111111");
    }

    @Test
    void expiresReferencesWithoutPersistingThem() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T00:00:00Z"));
        ConversationReferenceStore store = new ConversationReferenceStore(
                Duration.ofMinutes(5),
                20,
                clock);
        store.rememberGroup(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.OCS_SESSION,
                List.of(new ConversationReferenceStore.ReferenceDraft(
                        "OS-12345678",
                        "当前课程",
                        Map.of("state", "RUNNING"))));

        clock.advance(Duration.ofMinutes(5));

        assertThat(store.resolve(
                "wechat|a",
                ConversationReferenceStore.ReferenceType.OCS_SESSION,
                "进度怎么样")).isEmpty();
        assertThat(store.size("wechat|a")).isZero();
    }

    private static ConversationReferenceStore.ReferenceDraft draft(
            String identifier,
            String label) {
        return new ConversationReferenceStore.ReferenceDraft(identifier, label, Map.of());
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
