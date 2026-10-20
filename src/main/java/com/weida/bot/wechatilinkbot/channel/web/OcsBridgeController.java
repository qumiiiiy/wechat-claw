package com.weida.bot.wechatilinkbot.channel.web;

import com.weida.bot.wechatilinkbot.agent.OcsQuestionAnswerException;
import com.weida.bot.wechatilinkbot.agent.OcsQuestionAnswerer;
import com.weida.bot.wechatilinkbot.capability.OcsStudyCapability;
import com.weida.bot.wechatilinkbot.model.OcsAnswerCandidate;
import com.weida.bot.wechatilinkbot.model.OcsAnswerCommand;
import com.weida.bot.wechatilinkbot.model.OcsAnswerDecision;
import com.weida.bot.wechatilinkbot.model.OcsLoginQrRelay;
import com.weida.bot.wechatilinkbot.model.OcsPluginConfig;
import com.weida.bot.wechatilinkbot.model.OcsQuestionSnapshot;
import com.weida.bot.wechatilinkbot.model.OcsStudySession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Loopback-only HTTP bridge used by the separately installed OCS companion script.
 */
@RestController
@RequestMapping("/api/local/ocs")
@ConditionalOnBean(OcsStudyCapability.class)
public class OcsBridgeController {

    static final String CLIENT_HEADER = "X-Wechat-Claw-Ocs-Client";
    static final String COMPANION_CLIENT = "companion-v1";

    private final OcsStudyCapability studyCapability;
    private final OcsQuestionAnswerer questionAnswerer;

    public OcsBridgeController(
            OcsStudyCapability studyCapability,
            OcsQuestionAnswerer questionAnswerer) {
        this.studyCapability = studyCapability;
        this.questionAnswerer = questionAnswerer;
    }

    @PostMapping("/sessions/find")
    public ResponseEntity<OcsStudySession> findPendingSession(
            @RequestHeader(name = CLIENT_HEADER, required = false) String clientMarker,
            @RequestBody(required = false) OcsFindSessionRequest findRequest,
            HttpServletRequest request) {
        if (!isTrustedLocalRequest(request, clientMarker)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String hostHint = findRequest == null ? "" : findRequest.hostHint();
        OcsStudySession session = studyCapability.findPendingSession(hostHint);
        if (session == null) {
            return ResponseEntity.noContent().build();
        }
        studyCapability.markCompanionConnected(session.sessionId());
        return ResponseEntity.ok(session);
    }

    @PostMapping("/sessions/{sessionId}/events")
    public ResponseEntity<OcsStudySession> recordEvent(
            @PathVariable String sessionId,
            @RequestHeader(name = CLIENT_HEADER, required = false) String clientMarker,
            @Valid @RequestBody OcsStudyEventRequest event,
            HttpServletRequest request) {
        if (!isTrustedLocalRequest(request, clientMarker)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        OcsStudySession session = studyCapability.recordEvent(
                sessionId,
                event.eventType(),
                event.lessonTitle(),
                event.content(),
                event.progressPercent());
        return ResponseEntity.ok(session);
    }

    @PostMapping("/sessions/{sessionId}/questions")
    public ResponseEntity<OcsAnswerCommand> answerQuestion(
            @PathVariable String sessionId,
            @RequestHeader(name = CLIENT_HEADER, required = false) String clientMarker,
            @Valid @RequestBody OcsQuestionRequest questionRequest,
            HttpServletRequest request) {
        if (!isTrustedLocalRequest(request, clientMarker)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var question = questionRequest.toSnapshot(Instant.now());
        if (!studyCapability.beginQuestion(sessionId, question.questionId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            OcsAnswerCandidate candidate = questionAnswerer.answer(question);
            OcsAnswerDecision decision = studyCapability.answerReady(sessionId, candidate);
            return ResponseEntity.ok(new OcsAnswerCommand(
                    sessionId,
                    decision.questionId(),
                    decision.action(),
                    decision.selectedOptionIds(),
                    decision.textAnswer(),
                    decision.confidence()));
        } catch (OcsQuestionAnswerException exception) {
            studyCapability.answerFailed(
                    sessionId,
                    question.questionId(),
                    exception.getMessage());
            return ResponseEntity.status(answerFailureStatus(exception.reason())).build();
        }
    }

    @PostMapping("/sessions/{sessionId}/answers/results")
    public ResponseEntity<OcsStudySession> recordAnswerResult(
            @PathVariable String sessionId,
            @RequestHeader(name = CLIENT_HEADER, required = false) String clientMarker,
            @Valid @RequestBody OcsAnswerResultRequest result,
            HttpServletRequest request) {
        if (!isTrustedLocalRequest(request, clientMarker)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(studyCapability.recordAnswerResult(
                sessionId,
                result.questionId(),
                result.result(),
                result.content()));
    }

    @PostMapping("/sessions/{sessionId}/companion/ready")
    public ResponseEntity<OcsPluginConfig> companionReady(
            @PathVariable String sessionId,
            @RequestHeader(name = CLIENT_HEADER, required = false) String clientMarker,
            @Valid @RequestBody OcsCompanionReadyRequest readyRequest,
            HttpServletRequest request) {
        if (!isTrustedLocalRequest(request, clientMarker)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            OcsPluginConfig config = studyCapability.reportCompanionReady(
                    sessionId,
                    readyRequest.companionVersion(),
                    readyRequest.pluginVersion(),
                    readyRequest.pageUrl());
            return ResponseEntity.ok(config);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/sessions/{sessionId}/config/applied")
    public ResponseEntity<OcsStudySession> pluginConfigApplied(
            @PathVariable String sessionId,
            @RequestHeader(name = CLIENT_HEADER, required = false) String clientMarker,
            @Valid @RequestBody OcsPluginConfigAppliedRequest appliedRequest,
            HttpServletRequest request) {
        if (!isTrustedLocalRequest(request, clientMarker)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(studyCapability.reportPluginConfigApplied(
                    sessionId,
                    appliedRequest.applied(),
                    appliedRequest.missingKeys()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/sessions/{sessionId}/login/qr")
    public ResponseEntity<OcsStudySession> loginQrRelay(
            @PathVariable String sessionId,
            @RequestHeader(name = CLIENT_HEADER, required = false) String clientMarker,
            @Valid @RequestBody OcsLoginQrRequest qrRequest,
            HttpServletRequest request) {
        if (!isTrustedLocalRequest(request, clientMarker)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(studyCapability.reportLoginQr(
                    sessionId,
                    qrRequest.qrDataUrl(),
                    qrRequest.pageUrl()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).build();
        }
    }

    @PostMapping("/sessions/{sessionId}/login/confirmed")
    public ResponseEntity<OcsStudySession> loginConfirmed(
            @PathVariable String sessionId,
            @RequestHeader(name = CLIENT_HEADER, required = false) String clientMarker,
            HttpServletRequest request) {
        if (!isTrustedLocalRequest(request, clientMarker)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(studyCapability.reportLoginConfirmed(sessionId));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/sessions/{sessionId}/plugin/missing")
    public ResponseEntity<OcsStudySession> pluginMissing(
            @PathVariable String sessionId,
            @RequestHeader(name = CLIENT_HEADER, required = false) String clientMarker,
            @Valid @RequestBody OcsPluginMissingRequest missingRequest,
            HttpServletRequest request) {
        if (!isTrustedLocalRequest(request, clientMarker)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            return ResponseEntity.ok(studyCapability.reportPluginMissing(
                    sessionId,
                    missingRequest.reason()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/questions/answer")
    public ResponseEntity<OcsQuestionBankResponse> answerFromQuestionBank(
            @RequestHeader(name = CLIENT_HEADER, required = false) String clientMarker,
            @Valid @RequestBody OcsQuestionBankRequest bankRequest,
            HttpServletRequest request) {
        if (!isTrustedLocalRequest(request, clientMarker)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var question = bankRequest.toSnapshot(Instant.now());
        if (question.type() == OcsQuestionSnapshot.QuestionType.UNKNOWN) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).build();
        }
        try {
            OcsAnswerCandidate candidate = questionAnswerer.answer(question);
            if (candidate.confidence() < questionAnswerer.minimumConfidence()) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).build();
            }
            return ResponseEntity.ok(OcsQuestionBankResponse.fromCandidate(
                    candidate.questionId(),
                    candidate.selectedOptionIds(),
                    candidate.textAnswer(),
                    candidate.confidence(),
                    candidate.rationale(),
                    candidate.source()));
        } catch (OcsQuestionAnswerException exception) {
            return ResponseEntity.status(answerFailureStatus(exception.reason())).build();
        }
    }

    private static HttpStatus answerFailureStatus(OcsQuestionAnswerException.Reason reason) {
        return switch (reason) {
            case LOW_CONFIDENCE, UNSUPPORTED, INVALID_RESPONSE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case OVERLOADED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE, TIMEOUT, PROVIDER_FAILURE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static boolean isTrustedLocalRequest(
            HttpServletRequest request,
            String clientMarker) {
        return isLoopbackAddress(request.getRemoteAddr())
                && COMPANION_CLIENT.equals(clientMarker);
    }

    private static boolean isLoopbackAddress(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(remoteAddress.strip()).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
