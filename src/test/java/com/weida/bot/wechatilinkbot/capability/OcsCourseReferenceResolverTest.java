package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.infrastructure.learning.CourseJsonParser.CourseDetail;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OcsCourseReferenceResolverTest {

    private static final String OWNER = "wechat-ilink|synthetic-owner";

    private OcsCourseReferenceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OcsCourseReferenceResolver();
    }

    @Test
    void resolvesRememberedBotReferenceToStableChaoxingIdentifiers() {
        CourseDetail selected = course("3002", "4002", "5002", "目标合成课程");
        resolver.remember(OWNER, List.of(
                course("3001", "4001", "5001", "第一门合成课程"),
                selected), selected);

        URI target = resolver.resolve(OWNER, "c2", "【目标-合成课程】");

        assertThat(target)
                .hasHost("mooc2-ans.chaoxing.com")
                .hasParameter("courseid", "3002")
                .hasParameter("clazzid", "4002")
                .hasParameter("cpi", "5002")
                .hasParameter("ut", "s");
        assertThat(resolver.resolveLatestSelection(OWNER, "目标合成课程"))
                .get()
                .extracting(OcsCourseReferenceResolver.ResolvedCourse::courseReference)
                .isEqualTo("C2");
    }

    @Test
    void rejectsMissingOrCrossConversationReference() {
        resolver.remember(OWNER, List.of(
                course("3001", "4001", "5001", "目标课程")));

        assertThatThrownBy(() -> resolver.resolve(
                "wechat-ilink|other-owner",
                "C1",
                "目标课程"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("课程引用已失效");
        resolver.clear(OWNER);
        assertThatThrownBy(() -> resolver.resolve(OWNER, "C1", "目标课程"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重新发送“查课程”");
    }

    @Test
    void doesNotInventLatestSelectionFromCourseListAlone() {
        resolver.remember(OWNER, List.of(
                course("3001", "4001", "5001", "目标课程")));

        assertThat(resolver.resolveLatestSelection(OWNER, "目标课程")).isEmpty();
    }

    @Test
    void rejectsReferenceWhenRememberedCourseNameDoesNotMatch() {
        resolver.remember(OWNER, List.of(
                course("3001", "4001", "5001", "另一门课程")));

        assertThatThrownBy(() -> resolver.resolve(OWNER, "C1", "目标课程"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("课程引用与课程名称不一致");
    }

    @Test
    void remembersMoreThanFiftyCoursesWithoutTruncatingReferences() {
        List<CourseDetail> details = new ArrayList<>();
        for (int index = 1; index <= 51; index++) {
            details.add(course(
                    String.valueOf(3000 + index),
                    String.valueOf(4000 + index),
                    String.valueOf(5000 + index),
                    "课程" + index));
        }
        resolver.remember(OWNER, details);

        URI target = resolver.resolve(OWNER, "C51", "课程51");

        assertThat(target)
                .hasParameter("courseid", "3051")
                .hasParameter("clazzid", "4051")
                .hasParameter("cpi", "5051");
    }

    @Test
    void includesCourseEncInResolvedTargetUrl() {
        CourseDetail selected = new CourseDetail(
                "4002",
                "3002",
                "5002",
                "目标合成课程",
                "合成班级",
                "合成教师",
                "合成学校",
                10,
                "",
                "",
                1,
                "",
                "a45c62790d497598ab07d7f526dbefef");
        resolver.remember(OWNER, List.of(selected), selected);

        URI target = resolver.resolve(OWNER, "C1", "目标合成课程");

        assertThat(target)
                .hasParameter("courseid", "3002")
                .hasParameter("enc", "a45c62790d497598ab07d7f526dbefef");
    }

    @Test
    void fallsBackToSessionEncWhenCourseDetailEncIsMissing() {
        CourseDetail selected = course("3002", "4002", "5002", "目标合成课程");

        resolver.remember(
                OWNER,
                List.of(selected),
                selected,
                "91d64bce03932a4e57dc17955fe19eb8");

        URI target = resolver.resolve(OWNER, "C1", "目标合成课程");

        assertThat(target)
                .hasParameter("courseid", "3002")
                .hasParameter("enc", "91d64bce03932a4e57dc17955fe19eb8");
    }

    @Test
    void dropsInvalidCourseEncFromResolvedTargetUrl() {
        CourseDetail selected = new CourseDetail(
                "4002",
                "3002",
                "5002",
                "目标合成课程",
                "合成班级",
                "合成教师",
                "合成学校",
                10,
                "",
                "",
                1,
                "",
                "bad enc value!");
        resolver.remember(OWNER, List.of(selected), selected);

        URI target = resolver.resolve(OWNER, "C1", "目标合成课程");

        assertThat(target.toString()).doesNotContain("enc=");
    }

    private static CourseDetail course(
            String courseId,
            String classId,
            String cpi,
            String courseName) {
        return new CourseDetail(
                classId,
                courseId,
                cpi,
                courseName,
                "合成班级",
                "合成教师",
                "合成学校",
                10,
                "",
                "",
                1,
                "",
                "");
    }
}
