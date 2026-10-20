package com.weida.bot.wechatilinkbot.channel.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.agent.OcsQuestionAnswerException;
import com.weida.bot.wechatilinkbot.agent.OcsQuestionAnswerer;
import com.weida.bot.wechatilinkbot.capability.OcsStudyCapability;
import com.weida.bot.wechatilinkbot.capability.OcsStudyProperties;
import com.weida.bot.wechatilinkbot.infrastructure.desktop.WindowsDesktopTaskExecutor;
import com.weida.bot.wechatilinkbot.model.OcsAnswerCandidate;
import com.weida.bot.wechatilinkbot.model.OcsPluginConfig;
import com.weida.bot.wechatilinkbot.model.OcsQuestionOption;
import com.weida.bot.wechatilinkbot.model.OcsQuestionSnapshot;
import com.weida.bot.wechatilinkbot.model.OcsStudySession;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.mockito.ArgumentCaptor;

class OcsBridgeControllerTest {

    private static final String CLIENT = "companion-v1";
    private static final String OWNER = "wechat-ilink|synthetic-owner";

    private OcsStudyCapability capability;
    private OcsBridgeController controller;
    private HttpServletRequest localRequest;
    private WindowsDesktopTaskExecutor desktopTaskExecutor;
    private OcsQuestionAnswerer questionAnswerer;

    @BeforeEach
    void setUp() {
        OcsStudyProperties properties = new OcsStudyProperties();
        properties.setEnabled(true);
        desktopTaskExecutor = mock(WindowsDesktopTaskExecutor.class);
        when(desktopTaskExecutor.available()).thenReturn(true);
        capability = new OcsStudyCapability(properties, desktopTaskExecutor);
        questionAnswerer = mock(OcsQuestionAnswerer.class);
        controller = new OcsBridgeController(capability, questionAnswerer);
        localRequest = mock(HttpServletRequest.class);
        when(localRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void letsAuthenticatedLoopbackCompanionReportReadyAndEvent() {
        OcsStudySession session = capability.startSession(
                OWNER,
                "合成课程");

        // Verify the browser was opened to the course page
        ArgumentCaptor<URI> urlCaptor = ArgumentCaptor.forClass(URI.class);
        verify(desktopTaskExecutor).openUrl(urlCaptor.capture());
        assertThat(urlCaptor.getValue().getHost()).isEqualTo("mooc1.chaoxing.com");

        // Companion reports ready via new endpoint
        // (tested via direct capability call since controller endpoint will be added in Phase 4)
        OcsPluginConfig config = capability.reportCompanionReady(
                session.sessionId(), "0.7.0", "4.0.0", "https://chaoxing.com");
        assertThat(config).isNotNull();

        ResponseEntity<OcsStudySession> response = controller.recordEvent(
                session.sessionId(),
                CLIENT,
                new OcsStudyEventRequest("STARTED", "第一章", "", 5),
                localRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(OcsStudySession.Status.RUNNING);
    }

    @Test
    void rejectsRemoteOrRequestsWithoutTheCompanionMarker() {
        OcsStudySession session = capability.startSession(
                OWNER,
                "合成课程");
        HttpServletRequest remoteRequest = mock(HttpServletRequest.class);
        when(remoteRequest.getRemoteAddr()).thenReturn("203.0.113.10");

        assertThat(controller.recordEvent(
                session.sessionId(),
                null,
                new OcsStudyEventRequest("STARTED", "", "", 0),
                remoteRequest).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.recordEvent(
                session.sessionId(),
                null,
                new OcsStudyEventRequest("STARTED", "", "", 0),
                localRequest).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.recordEvent(
                session.sessionId(),
                "unknown-client",
                new OcsStudyEventRequest("STARTED", "", "", 0),
                localRequest).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void returnsValidatedOnlineAnswerCommandAndRejectsDuplicateQuestion() {
        OcsStudySession session = capability.startSession(OWNER, "合成课程");
        when(questionAnswerer.answer(org.mockito.ArgumentMatchers.any(OcsQuestionSnapshot.class)))
                .thenReturn(new OcsAnswerCandidate(
                        "question-1",
                        List.of("B"),
                        "",
                        "合成依据",
                        0.91,
                        "AGENT_MODEL_ONLINE"));
        OcsQuestionRequest question = new OcsQuestionRequest(
                "question-1",
                "SINGLE_CHOICE",
                "完全合成的题目",
                List.of(
                        new OcsQuestionOption("A", "甲"),
                        new OcsQuestionOption("B", "乙")),
                "第一章",
                "");

        var response = controller.answerQuestion(
                session.sessionId(), CLIENT, question, localRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().selectedOptionIds()).containsExactly("B");
        var verified = controller.recordAnswerResult(
                session.sessionId(),
                CLIENT,
                new OcsAnswerResultRequest("question-1", "CORRECT", "页面确认正确"),
                localRequest);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verified.getBody()).isNotNull();
        assertThat(verified.getBody().status()).isEqualTo(OcsStudySession.Status.RUNNING);
        assertThat(controller.answerQuestion(
                session.sessionId(), CLIENT, question, localRequest).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reportsLowConfidenceWithoutReturningBrowserAction() {
        OcsStudySession session = capability.startSession(OWNER, "合成课程");
        when(questionAnswerer.answer(org.mockito.ArgumentMatchers.any(OcsQuestionSnapshot.class)))
                .thenThrow(new OcsQuestionAnswerException(
                        OcsQuestionAnswerException.Reason.LOW_CONFIDENCE,
                        "置信度不足"));
        OcsQuestionRequest question = new OcsQuestionRequest(
                "question-low",
                "TRUE_FALSE",
                "完全合成的判断题",
                List.of(
                        new OcsQuestionOption("A", "正确"),
                        new OcsQuestionOption("B", "错误")),
                "",
                "");

        var response = controller.answerQuestion(
                session.sessionId(), CLIENT, question, localRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).isNull();
        assertThat(capability.getStatus(OWNER, session.sessionId()).status())
                .isEqualTo(OcsStudySession.Status.WAITING_QUESTION);
    }

}
