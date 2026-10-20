package com.weida.bot.wechatilinkbot.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.ParseStatus;
import com.weida.bot.wechatilinkbot.model.QuestionOption;
import com.weida.bot.wechatilinkbot.model.QuestionSnapshot;
import com.weida.bot.wechatilinkbot.model.QuestionType;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestionDeduplicatorTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void firstOccurrenceNotDuplicate() {
        Set<String> seen = new HashSet<>();
        assertThat(QuestionDeduplicator.isDuplicate("sha256:abc123", seen)).isFalse();
    }

    @Test
    void secondOccurrenceIsDuplicate() {
        Set<String> seen = new HashSet<>();
        seen.add("sha256:abc123");
        assertThat(QuestionDeduplicator.isDuplicate("sha256:abc123", seen)).isTrue();
    }

    @Test
    void differentFingerprintsNotDuplicate() {
        Set<String> seen = new HashSet<>();
        seen.add("sha256:abc123");
        assertThat(QuestionDeduplicator.isDuplicate("sha256:def456", seen)).isFalse();
    }

    @Test
    void nullFingerprintNotDuplicate() {
        Set<String> seen = new HashSet<>();
        assertThat(QuestionDeduplicator.isDuplicate(null, seen)).isFalse();
    }

    @Test
    void blankFingerprintNotDuplicate() {
        Set<String> seen = new HashSet<>();
        assertThat(QuestionDeduplicator.isDuplicate("  ", seen)).isFalse();
    }

    @Test
    void deduplicateListKeepsFirst() {
        QuestionSnapshot first = snapshot("sha256:aaa", "第一题题干");
        QuestionSnapshot second = snapshot("sha256:aaa", "第一题题干");
        QuestionSnapshot third = snapshot("sha256:bbb", "第二题题干");

        List<QuestionSnapshot> result = QuestionDeduplicator.deduplicate(List.of(first, second, third));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).stem()).isEqualTo("第一题题干");
        assertThat(result.get(1).stem()).isEqualTo("第二题题干");
    }

    @Test
    void deduplicateEmptyList() {
        assertThat(QuestionDeduplicator.deduplicate(List.of())).isEmpty();
    }

    @Test
    void deduplicateNullList() {
        assertThat(QuestionDeduplicator.deduplicate(null)).isEmpty();
    }

    @Test
    void deduplicateAllUnique() {
        QuestionSnapshot a = snapshot("sha256:aaa", "题一");
        QuestionSnapshot b = snapshot("sha256:bbb", "题二");
        QuestionSnapshot c = snapshot("sha256:ccc", "题三");

        List<QuestionSnapshot> result = QuestionDeduplicator.deduplicate(List.of(a, b, c));
        assertThat(result).hasSize(3);
    }

    @Test
    void deduplicateSkipsBlankFingerprint() {
        QuestionSnapshot valid = snapshot("sha256:aaa", "题一");
        QuestionSnapshot blank = snapshot("", "题二");

        List<QuestionSnapshot> result = QuestionDeduplicator.deduplicate(List.of(valid, blank));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).stem()).isEqualTo("题一");
    }

    private static QuestionSnapshot snapshot(String fingerprint, String stem) {
        return new QuestionSnapshot(
                "q-" + fingerprint.hashCode(),
                fingerprint,
                "course-1",
                "chapter-1",
                QuestionType.SINGLE_CHOICE,
                stem,
                List.of(new QuestionOption("A", "选项一")),
                false,
                "https://synthetic.example/test",
                NOW,
                ParseStatus.OK);
    }
}
