package com.weida.bot.wechatilinkbot.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class QuestionDomainRecognizerTest {

    @Test
    void chaoxingMooc1Recognized() {
        assertThat(QuestionDomainRecognizer.isChaoxingUrl("https://mooc1.chaoxing.com/mycourse")).isTrue();
    }

    @Test
    void chaoxingMooc2Recognized() {
        assertThat(QuestionDomainRecognizer.isChaoxingUrl("https://mooc2.chaoxing.com/course/123")).isTrue();
    }

    @Test
    void chaoxingMooc2AnsRecognized() {
        assertThat(QuestionDomainRecognizer.isChaoxingUrl("https://mooc2-ans.chaoxing.com/mooc2-ans/work/list")).isTrue();
    }

    @Test
    void chaoxingRootRecognized() {
        assertThat(QuestionDomainRecognizer.isChaoxingUrl("https://chaoxing.com/")).isTrue();
    }

    @Test
    void chaoxingPassportRecognized() {
        assertThat(QuestionDomainRecognizer.isChaoxingUrl("https://passport2.chaoxing.com/login")).isTrue();
    }

    @Test
    void chaoxingIMoocRecognized() {
        assertThat(QuestionDomainRecognizer.isChaoxingUrl("https://i.mooc.chaoxing.com/space")).isTrue();
    }

    @Test
    void nonChaoxingDomainRejected() {
        assertThat(QuestionDomainRecognizer.isChaoxingUrl("https://www.example.com")).isFalse();
    }

    @Test
    void nullUrlReturnsFalse() {
        assertThat(QuestionDomainRecognizer.isChaoxingUrl(null)).isFalse();
    }

    @Test
    void blankUrlReturnsFalse() {
        assertThat(QuestionDomainRecognizer.isChaoxingUrl("  ")).isFalse();
    }

    @Test
    void invalidUrlReturnsFalse() {
        assertThat(QuestionDomainRecognizer.isChaoxingUrl("not-a-url")).isFalse();
    }

    @Test
    void extractCourseIdFromQueryParam() {
        Optional<String> id = QuestionDomainRecognizer.extractCourseId(
                "https://mooc1.chaoxing.com/mooc2/work/list?courseId=12345&classId=67890");
        assertThat(id).hasValue("12345");
    }

    @Test
    void extractCourseIdFromPath() {
        Optional<String> id = QuestionDomainRecognizer.extractCourseId(
                "https://mooc1.chaoxing.com/course/98765");
        assertThat(id).hasValue("98765");
    }

    @Test
    void extractCourseIdWhenMissing() {
        Optional<String> id = QuestionDomainRecognizer.extractCourseId(
                "https://mooc1.chaoxing.com/mycourse");
        assertThat(id).isEmpty();
    }

    @Test
    void extractCourseIdFromNull() {
        assertThat(QuestionDomainRecognizer.extractCourseId(null)).isEmpty();
    }

    @Test
    void matchesCourseNameExact() {
        assertThat(QuestionDomainRecognizer.matchesCourseName("高等数学", "高等数学")).isTrue();
    }

    @Test
    void matchesCourseNamePartial() {
        assertThat(QuestionDomainRecognizer.matchesCourseName("高等数学（2026春季）", "高等数学")).isTrue();
    }

    @Test
    void matchesCourseNameCaseInsensitive() {
        assertThat(QuestionDomainRecognizer.matchesCourseName("Computer Network", "computer")).isTrue();
    }

    @Test
    void matchesCourseNameNoMatch() {
        assertThat(QuestionDomainRecognizer.matchesCourseName("大学英语", "数学")).isFalse();
    }

    @Test
    void matchesCourseNameNullInputs() {
        assertThat(QuestionDomainRecognizer.matchesCourseName(null, "test")).isFalse();
        assertThat(QuestionDomainRecognizer.matchesCourseName("test", null)).isFalse();
    }

    @Test
    void normalizePageSourceRemovesQuery() {
        String result = QuestionDomainRecognizer.normalizePageSource(
                "https://mooc1.chaoxing.com/course/123?token=abc&sid=xyz");
        assertThat(result).isEqualTo("https://mooc1.chaoxing.com/course/123");
    }

    @Test
    void normalizePageSourceRemovesFragment() {
        String result = QuestionDomainRecognizer.normalizePageSource(
                "https://mooc1.chaoxing.com/course/123#section1");
        assertThat(result).isEqualTo("https://mooc1.chaoxing.com/course/123");
    }

    @Test
    void normalizePageSourceHandlesNull() {
        assertThat(QuestionDomainRecognizer.normalizePageSource(null)).isEmpty();
    }

    @Test
    void normalizePageSourceHandlesBlank() {
        assertThat(QuestionDomainRecognizer.normalizePageSource("  ")).isEmpty();
    }
}
