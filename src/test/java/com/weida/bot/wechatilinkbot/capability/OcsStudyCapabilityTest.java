package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.infrastructure.desktop.WindowsDesktopTaskExecutor;
import com.weida.bot.wechatilinkbot.capability.OcsCourseReferenceResolver.ResolvedCourse;
import com.weida.bot.wechatilinkbot.model.OcsAnswerCandidate;
import com.weida.bot.wechatilinkbot.model.OcsAnswerDecision;
import com.weida.bot.wechatilinkbot.model.OcsPluginConfig;
import com.weida.bot.wechatilinkbot.model.OcsStudyCommand;
import com.weida.bot.wechatilinkbot.model.OcsStudySession;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OcsStudyCapabilityTest {

    private static final String OWNER = "wechat-ilink|synthetic-owner";
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    private OcsStudyCapability capability;
    private WindowsDesktopTaskExecutor desktopTaskExecutor;
    private OcsCourseReferenceResolver courseReferenceResolver;

    @BeforeEach
    void setUp() {
        OcsStudyProperties properties = new OcsStudyProperties();
        properties.setEnabled(true);
        properties.setMaxSessions(5);
        properties.setMaxEventCharacters(1000);
        properties.setMaxNotesCharacters(2000);
        properties.setSessionExpireAfter(Duration.ofHours(1));
        desktopTaskExecutor = mock(WindowsDesktopTaskExecutor.class);
        courseReferenceResolver = mock(OcsCourseReferenceResolver.class);
        when(desktopTaskExecutor.available()).thenReturn(true);
        when(courseReferenceResolver.resolve(OWNER, "C17", "合成课程"))
                .thenReturn(URI.create(
                        "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse"
                                + "?courseid=3101&clazzid=4101&cpi=5101&ut=s"));
        capability = new OcsStudyCapability(
                properties,
                desktopTaskExecutor,
                courseReferenceResolver,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsSessionAndBuildsNotesFromCompanionEvents() {
        OcsStudySession created = capability.startSession(
                OWNER,
                "合成课程",
                "c17");

        URI openedUrl = openedUrl();
        assertThat(openedUrl).hasHost("mooc2-ans.chaoxing.com");
        assertThat(openedUrl).hasParameter("courseid", "3101");
        assertThat(openedUrl).hasParameter("clazzid", "4101");
        assertThat(openedUrl).hasParameter("cpi", "5101");

        OcsPluginConfig config = capability.reportCompanionReady(
                created.sessionId(), "0.7.0", "4.0.0", "https://chaoxing.com");
        assertThat(config.videoReview()).isTrue();
        assertThat(config.autoChapterSwitch()).isTrue();

        capability.recordEvent(
                created.sessionId(),
                "LESSON_CHANGED",
                "第一章",
                "",
                10);
        OcsStudySession updated = capability.recordEvent(
                created.sessionId(),
                "CAPTION",
                "第一章",
                "这是完全合成的字幕。",
                25);

        assertThat(updated.status()).isEqualTo(OcsStudySession.Status.RUNNING);
        assertThat(updated.progressPercent()).isEqualTo(25);
        assertThat(updated.currentLesson()).isEqualTo("第一章");
        assertThat(updated.notes())
                .contains("【章节】第一章")
                .contains("这是完全合成的字幕。");
    }

    @Test
    void rejectsInvalidCourseReferenceBeforeOpeningBrowser() {
        assertThatThrownBy(() -> capability.startSession(
                OWNER,
                "合成课程",
                "course-17"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("C1 至 C999");
    }

    @Test
    void reusesLatestSelectedCourseWhenModelOmitsReference() {
        URI targetUrl = URI.create(
                "https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/studentcourse"
                        + "?courseid=3201&clazzid=4201&cpi=5201&ut=s");
        when(courseReferenceResolver.resolveLatestSelection(OWNER, "合成课程"))
                .thenReturn(Optional.of(new ResolvedCourse("C17", targetUrl)));

        OcsStudySession session = capability.startSession(OWNER, "合成课程", "");

        URI openedUrl = openedUrl();
        assertThat(openedUrl).isEqualTo(targetUrl);
        assertThat(session.status()).isEqualTo(OcsStudySession.Status.CREATED);
    }

    @Test
    void carriesRequestedChapterNameIntoSessionSnapshot() {
        OcsStudySession session = capability.startSession(
                OWNER,
                "合成课程",
                "c17",
                "第三章 数据结构");

        assertThat(session.chapterName()).isEqualTo("第三章 数据结构");
    }

    @Test
    void rejectsUnsafeConfiguredStartUrlWithoutCreatingACommand() {
        OcsStudyProperties unsafeProperties = new OcsStudyProperties();
        unsafeProperties.setEnabled(true);
        unsafeProperties.setChaoxingStartUrl("http://mooc1.chaoxing.com/mycourse");
        OcsStudyCapability unsafeCapability = new OcsStudyCapability(
                unsafeProperties,
                desktopTaskExecutor,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> unsafeCapability.startSession(
                OWNER,
                "合成课程"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void rejectsWrongOwnerAndEventsAfterCompletion() {
        OcsStudySession created = capability.startSession(
                OWNER,
                "合成课程");

        assertThatThrownBy(() -> capability.getStatus(
                "wechat-ilink|other-user",
                created.sessionId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("本人");
        capability.recordEvent(
                created.sessionId(),
                "FINISHED",
                "",
                "",
                100);
        assertThatThrownBy(() -> capability.recordEvent(
                created.sessionId(),
                "STARTED",
                "",
                "",
                0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("结束");
    }

    @Test
    void allowsAnotherAuthenticatedConversationToCreateItsOwnSession() {
        OcsStudySession created = capability.startSession(
                "wechat-ilink|another-owner",
                "另一门课程");

        assertThat(created.courseName()).isEqualTo("另一门课程");
        assertThat(capability.getStatus(
                "wechat-ilink|another-owner",
                created.sessionId())).isEqualTo(created);
    }

    @Test
    void keepsExplicitOperatorRestrictionsOptionalAndBackwardCompatible() {
        OcsStudyProperties restrictedProperties = new OcsStudyProperties();
        restrictedProperties.setEnabled(true);
        restrictedProperties.setAllowedConversationList(List.of(OWNER));
        restrictedProperties.setAllowedUrlHostList(List.of("courses.example.edu.cn"));
        restrictedProperties.setChaoxingStartUrl("https://other-platform.example/mycourse");
        OcsStudyCapability restrictedCapability = new OcsStudyCapability(
                restrictedProperties,
                desktopTaskExecutor,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> restrictedCapability.startSession(
                "wechat-ilink|other-owner",
                "合成课程"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限");
        assertThatThrownBy(() -> restrictedCapability.startSession(
                OWNER,
                "合成课程"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("允许列表");
    }

    @Test
    void reportsFailureAndDropsCommandWhenBrowserIsUnavailable() {
        when(desktopTaskExecutor.available()).thenReturn(false);

        OcsStudySession session = capability.startSession(OWNER, "合成课程");

        assertThat(session.status()).isEqualTo(OcsStudySession.Status.FAILED);
        assertThat(session.lastEvent()).contains("浏览器当前不可用");
    }

    @Test
    void companionReadyTransitionsToRunningAndReturnsPluginConfig() {
        OcsStudySession session = capability.startSession(OWNER, "合成课程");

        OcsPluginConfig config = capability.reportCompanionReady(
                session.sessionId(), "0.7.0", "4.0.0", "https://chaoxing.com");

        assertThat(config.videoReview()).isTrue();
        assertThat(config.autoChapterSwitch()).isTrue();
        assertThat(config.answerEnabled()).isTrue();
        assertThat(config.questionBankUrl()).contains("127.0.0.1");

        OcsStudySession after = capability.getStatus(OWNER, session.sessionId());
        assertThat(after.status()).isEqualTo(OcsStudySession.Status.RUNNING);
        assertThat(after.lastEvent()).contains("伴随脚本 0.7.0 已连接");
    }

    @Test
    void pluginMissingTransitionsToPluginMissingStatus() {
        OcsStudySession session = capability.startSession(OWNER, "合成课程");

        OcsStudySession missing = capability.reportPluginMissing(
                session.sessionId(), "未检测到 OCS 插件");

        assertThat(missing.status()).isEqualTo(OcsStudySession.Status.PLUGIN_MISSING);
        assertThat(missing.lastEvent()).contains("PLUGIN_MISSING");
    }

    @Test
    void recordsOnlineAnswerCandidateAndRejectsDuplicateQuestion() {
        OcsStudySession session = capability.startSession(OWNER, "合成课程");

        assertThat(capability.beginQuestion(session.sessionId(), "question-1")).isTrue();
        assertThat(capability.beginQuestion(session.sessionId(), "question-1")).isFalse();
        OcsAnswerDecision decision = capability.answerReady(
                session.sessionId(),
                new OcsAnswerCandidate(
                        "question-1",
                        List.of("B"),
                        "",
                        "合成依据",
                        0.9,
                        "AGENT_MODEL_ONLINE"));

        assertThat(decision.action()).isEqualTo("SELECT_OPTIONS");
        assertThat(decision.selectedOptionIds()).containsExactly("B");
        assertThat(decision.modelIdentifier()).isEqualTo("AGENT_MODEL_ONLINE");
        OcsStudySession verified = capability.recordAnswerResult(
                session.sessionId(),
                "question-1",
                "CORRECT",
                "页面确认正确");
        assertThat(verified.status()).isEqualTo(OcsStudySession.Status.RUNNING);
        assertThat(verified.lastEvent()).isEqualTo("ANSWER_CORRECT：页面确认正确");
    }

    @Test
    void rejectsAnswerResultForAnotherQuestion() {
        OcsStudySession session = capability.startSession(OWNER, "合成课程");
        capability.beginQuestion(session.sessionId(), "question-1");

        assertThatThrownBy(() -> capability.recordAnswerResult(
                session.sessionId(),
                "question-2",
                "CORRECT",
                ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不匹配");
    }

    @Test
    void limitsOnlineAnswerAttemptsWithinOneStudySession() {
        OcsStudyProperties properties = new OcsStudyProperties();
        properties.setEnabled(true);
        properties.setMaxAnswerAttemptsPerSession(1);
        OcsStudyCapability limitedCapability = new OcsStudyCapability(
                properties,
                desktopTaskExecutor,
                Clock.fixed(NOW, ZoneOffset.UTC));
        OcsStudySession session = limitedCapability.startSession(OWNER, "合成课程");

        assertThat(limitedCapability.beginQuestion(session.sessionId(), "question-1")).isTrue();
        assertThat(limitedCapability.beginQuestion(session.sessionId(), "question-1")).isFalse();
        assertThatThrownBy(() -> limitedCapability.beginQuestion(
                session.sessionId(),
                "question-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("次数上限");
    }

    private URI openedUrl() {
        ArgumentCaptor<URI> captor = ArgumentCaptor.forClass(URI.class);
        verify(desktopTaskExecutor).openUrl(captor.capture());
        return captor.getValue();
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
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
