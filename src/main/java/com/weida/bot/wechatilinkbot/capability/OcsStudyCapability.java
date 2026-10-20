package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.capability.OcsCourseReferenceResolver.ResolvedCourse;
import com.weida.bot.wechatilinkbot.infrastructure.desktop.WindowsDesktopTaskExecutionException;
import com.weida.bot.wechatilinkbot.infrastructure.desktop.WindowsDesktopTaskExecutor;
import com.weida.bot.wechatilinkbot.model.OcsAnswerCandidate;
import com.weida.bot.wechatilinkbot.model.OcsAnswerDecision;
import com.weida.bot.wechatilinkbot.model.OcsPluginConfig;
import com.weida.bot.wechatilinkbot.model.OcsPluginStatus;
import com.weida.bot.wechatilinkbot.model.OcsStudySession;
import com.weida.bot.wechatilinkbot.model.OcsStudySession.Status;
import com.weida.bot.wechatilinkbot.model.OcsLoginQrRelay;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Owns bounded OCS study sessions, launch leases, and verified browser events. */
@Component
@ConditionalOnProperty(
        prefix = "bot.capability.learning.ocs",
        name = "enabled",
        havingValue = "true")
public class OcsStudyCapability {

    private static final Logger LOG = LoggerFactory.getLogger(OcsStudyCapability.class);
    private static final int MAX_COURSE_NAME_CHARACTERS = 200;
    private static final String COURSE_REFERENCE_PATTERN = "C[1-9][0-9]{0,2}";
    private static final int MAX_URL_CHARACTERS = 2_048;
    private static final int MAX_LESSON_CHARACTERS = 300;

    private final OcsStudyProperties properties;
    private final WindowsDesktopTaskExecutor desktopTaskExecutor;
    private final OcsCourseReferenceResolver courseReferenceResolver;
    private final Clock clock;
    private final Map<String, SessionState> sessionMap = new LinkedHashMap<>();

    @Autowired
    public OcsStudyCapability(
            OcsStudyProperties properties,
            WindowsDesktopTaskExecutor desktopTaskExecutor,
            OcsCourseReferenceResolver courseReferenceResolver) {
        this(properties, desktopTaskExecutor, courseReferenceResolver, Clock.systemUTC());
    }

    public OcsStudyCapability(
            OcsStudyProperties properties,
            WindowsDesktopTaskExecutor desktopTaskExecutor) {
        this(properties, desktopTaskExecutor, null, Clock.systemUTC());
    }

    OcsStudyCapability(
            OcsStudyProperties properties,
            WindowsDesktopTaskExecutor desktopTaskExecutor,
            Clock clock) {
        this(properties, desktopTaskExecutor, null, clock);
    }

    OcsStudyCapability(
            OcsStudyProperties properties,
            WindowsDesktopTaskExecutor desktopTaskExecutor,
            OcsCourseReferenceResolver courseReferenceResolver,
            Clock clock) {
        this.properties = properties;
        this.desktopTaskExecutor = desktopTaskExecutor;
        this.courseReferenceResolver = courseReferenceResolver;
        this.clock = clock;
    }

    public boolean available() {
        return properties.isEnabled();
    }

    public synchronized OcsStudySession startSession(String conversationId, String courseName) {
        return startSession(conversationId, courseName, "", "");
    }

    public synchronized OcsStudySession startSession(
            String conversationId,
            String courseName,
            String courseReference) {
        return startSession(conversationId, courseName, courseReference, "");
    }

    public synchronized OcsStudySession startSession(
            String conversationId,
            String courseName,
            String courseReference,
            String chapterName) {
        requireAvailable();
        String owner = requireConversation(conversationId);
        String safeCourseName = requireBoundedText(
                courseName,
                "课程名称不能为空",
                MAX_COURSE_NAME_CHARACTERS);
        String safeChapterName = boundedOptionalText(
                chapterName,
                MAX_LESSON_CHARACTERS,
                "章节名称过长");
        String requestedCourseReference = normalizeCourseReference(courseReference);
        URI courseUrl = normalizeCourseUrl(properties.getChaoxingStartUrl());
        ResolvedCourse resolvedCourse = resolveTargetCourse(
                owner,
                requestedCourseReference,
                safeCourseName);
        String safeCourseReference = resolvedCourse == null
                ? requestedCourseReference
                : resolvedCourse.courseReference();
        URI targetCourseUrl = resolvedCourse == null ? null : resolvedCourse.targetUrl();
        pruneExpired();
        if (sessionMap.size() >= properties.getMaxSessions()) {
            throw new IllegalArgumentException("OCS 学习会话数量已达到上限");
        }

        Instant now = clock.instant();
        String sessionId = "OS-" + UUID.randomUUID();
        SessionState state = new SessionState(
                sessionId,
                owner,
                safeCourseName,
                safeCourseReference,
                safeChapterName,
                courseUrl,
                targetCourseUrl,
                "OC-" + UUID.randomUUID(),
                "OL-" + UUID.randomUUID(),
                now);
        sessionMap.put(sessionId, state);
        if (!desktopTaskExecutor.available()) {
            return markLaunchFailed(state, "本机浏览器当前不可用");
        }
        URI openUrl = targetCourseUrl != null ? targetCourseUrl : courseUrl;
        try {
            LOG.info(
                    "ocs_session_started sessionId={} courseReference={} openUrl={}",
                    sessionId,
                    safeCourseReference,
                    openUrl);
            desktopTaskExecutor.openUrl(openUrl);
            state.status = Status.CREATED;
            state.lastEvent = "已打开课程页面，等待伴随脚本连接";
            state.updatedAt = now;
            return state.snapshot();
        } catch (WindowsDesktopTaskExecutionException exception) {
            LOG.error(
                    "ocs_browser_launch_failed sessionId={} code=open_url_failed",
                    sessionId,
                    exception);
            return markLaunchFailed(state, "本机浏览器未能打开课程页面");
        }
    }

    public synchronized OcsStudySession getStatus(String conversationId, String sessionId) {
        requireAvailable();
        String owner = requireConversation(conversationId);
        pruneExpired();
        SessionState state = requireSession(sessionId);
        if (!state.conversationId.equals(owner)) {
            throw new IllegalArgumentException("只能查询本人创建的 OCS 学习会话");
        }
        return state.snapshot();
    }

    public synchronized OcsStudySession findPendingSession(String hostHint) {
        requireAvailable();
        pruneExpired();
        String safeHost = hostHint == null ? "" : hostHint.strip().toLowerCase(Locale.ROOT);
        for (SessionState state : sessionMap.values()) {
            if (isTerminal(state.status)) {
                continue;
            }
            if (state.companionConnected) {
                continue;
            }
            if (safeHost.isBlank()) {
                return state.snapshot();
            }
            String courseHost = state.courseUrl == null ? "" : state.courseUrl.getHost();
            String targetHost = state.targetCourseUrl == null ? "" : state.targetCourseUrl.getHost();
            if (courseHost.equalsIgnoreCase(safeHost) || targetHost.equalsIgnoreCase(safeHost)) {
                LOG.info(
                        "ocs_session_claimed sessionId={} host={}",
                        state.sessionId,
                        safeHost);
                return state.snapshot();
            }
        }
        return null;
    }

    public synchronized void markCompanionConnected(String sessionId) {
        requireAvailable();
        SessionState state = sessionMap.get(sessionId);
        if (state != null) {
            state.companionConnected = true;
        }
    }

    public synchronized boolean beginQuestion(String sessionId, String questionId) {
        requireAvailable();
        pruneExpired();
        SessionState state = requireSession(sessionId);
        requireNonTerminal(state);
        String safeQuestionId = requireBoundedText(questionId, "题目 ID 不能为空", 128);
        if (state.questionIds.contains(safeQuestionId)) {
            return false;
        }
        if (state.answerAttempts >= properties.getMaxAnswerAttemptsPerSession()) {
            throw new IllegalArgumentException("当前 OCS 学习会话已达到在线答题次数上限");
        }
        state.questionIds.add(safeQuestionId);
        state.answerAttempts++;
        state.currentQuestionId = safeQuestionId;
        state.status = Status.ANSWERING;
        state.lastEvent = "正在请求在线 AI 回答课程互动题";
        state.updatedAt = clock.instant();
        return true;
    }

    public synchronized OcsAnswerDecision answerReady(
            String sessionId,
            OcsAnswerCandidate candidate) {
        requireAvailable();
        SessionState state = requireSession(sessionId);
        requireNonTerminal(state);
        if (!candidate.questionId().equals(state.currentQuestionId)) {
            throw new IllegalArgumentException("题目尚未进入在线回答流程");
        }
        state.status = Status.WAITING_QUESTION;
        state.lastEvent = "在线 AI 已返回答案候选，等待页面验证";
        String candidateNote = "【AI 候选】置信度 "
                + Math.round(candidate.confidence() * 100)
                + "%"
                + (candidate.rationale().isBlank() ? "" : "：" + candidate.rationale());
        state.notes = appendNote(state.notes, candidateNote);
        state.updatedAt = clock.instant();
        String action = candidate.selectedOptionIds().isEmpty()
                ? "FILL_TEXT"
                : "SELECT_OPTIONS";
        return new OcsAnswerDecision(
                candidate.questionId(),
                action,
                candidate.selectedOptionIds(),
                candidate.textAnswer(),
                candidate.confidence(),
                candidate.source());
    }

    public synchronized OcsStudySession answerFailed(
            String sessionId,
            String questionId,
            String message) {
        requireAvailable();
        SessionState state = requireSession(sessionId);
        requireNonTerminal(state);
        if (!questionId.equals(state.currentQuestionId)) {
            throw new IllegalArgumentException("题目尚未进入在线回答流程");
        }
        state.status = Status.WAITING_QUESTION;
        state.lastEvent = "ANSWER_FAILED：" + boundedOptionalText(message, 300, "答题失败原因过长");
        state.updatedAt = clock.instant();
        return state.snapshot();
    }

    public synchronized OcsStudySession recordAnswerResult(
            String sessionId,
            String questionId,
            String rawResult,
            String content) {
        requireAvailable();
        SessionState state = requireSession(sessionId);
        requireNonTerminal(state);
        String safeQuestionId = requireBoundedText(questionId, "题目 ID 不能为空", 128);
        if (!safeQuestionId.equals(state.currentQuestionId)) {
            throw new IllegalArgumentException("页面结果与当前等待验证的题目不匹配");
        }
        AnswerResult result = parseAnswerResult(rawResult);
        String safeContent = boundedOptionalText(content, 1_000, "答题结果内容过长");
        state.status = switch (result) {
            case CORRECT -> Status.RUNNING;
            case SELECTED, SUBMITTED, WRONG, BLOCKED -> Status.WAITING_QUESTION;
        };
        state.lastEvent = "ANSWER_" + result.name()
                + (safeContent.isBlank() ? "" : "：" + safeContent);
        if (result == AnswerResult.CORRECT || result == AnswerResult.WRONG) {
            String note = result == AnswerResult.CORRECT
                    ? "【答题】页面确认正确"
                    : "【答题】页面反馈错误";
            state.notes = appendNote(state.notes, note);
        }
        if (result == AnswerResult.CORRECT) {
            state.currentQuestionId = "";
        }
        state.updatedAt = clock.instant();
        return state.snapshot();
    }

    public synchronized OcsStudySession recordEvent(
            String sessionId,
            String rawEventType,
            String lessonTitle,
            String content,
            Integer progressPercent) {
        requireAvailable();
        pruneExpired();
        SessionState state = requireSession(sessionId);
        requireNonTerminal(state);

        EventType eventType = parseEventType(rawEventType);
        String safeLesson = boundedOptionalText(lessonTitle, MAX_LESSON_CHARACTERS, "章节标题过长");
        String safeContent = boundedOptionalText(
                content,
                properties.getMaxEventCharacters(),
                "OCS 事件内容过长");
        int safeProgress = progressPercent == null ? state.progressPercent : progressPercent;
        if (safeProgress < 0 || safeProgress > 100) {
            throw new IllegalArgumentException("学习进度必须在 0 到 100 之间");
        }

        state.status = nextStatus(eventType);
        state.progressPercent = eventType == EventType.FINISHED ? 100 : safeProgress;
        if (!safeLesson.isBlank()) {
            state.currentLesson = safeLesson;
        }
        LOG.info(
                "ocs_session_event sessionId={} eventType={} lesson={} progress={}",
                sessionId,
                eventType,
                safeLesson,
                safeProgress);
        state.notes = updatedNotes(state.notes, eventType, safeLesson, safeContent);
        state.lastEvent = eventSummary(eventType, safeContent);
        state.updatedAt = clock.instant();
        return state.snapshot();
    }

    public synchronized void clearConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        String owner = conversationId.strip();
        sessionMap.values().removeIf(state -> state.conversationId.equals(owner));
    }

    public synchronized OcsPluginConfig reportCompanionReady(
            String sessionId,
            String companionVersion,
            String pluginVersion,
            String pageUrl) {
        requireAvailable();
        pruneExpired();
        SessionState state = requireSession(sessionId);
        requireNonTerminal(state);
        String safeCompanionVersion = requireBoundedText(
                companionVersion, "伴随脚本版本不能为空", 32);
        String safePluginVersion = boundedOptionalText(
                pluginVersion, 32, "插件版本过长");
        boundedOptionalText(pageUrl, MAX_URL_CHARACTERS, "页面地址过长");

        state.pluginStatus = new OcsPluginStatus(
                !safePluginVersion.isBlank(),
                safePluginVersion,
                false,
                List.of());
        if (state.status != Status.WAITING_LOGIN) {
            state.status = state.targetCourseUrl != null
                    ? Status.DISCOVERING
                    : Status.RUNNING;
        }
        state.lastEvent = "伴随脚本 " + safeCompanionVersion + " 已连接"
                + (safePluginVersion.isBlank() ? "，未检测到 OCS 插件" : "");
        LOG.info(
                "ocs_companion_ready sessionId={} companionVersion={} pluginVersion={}",
                sessionId,
                safeCompanionVersion,
                safePluginVersion);
        state.updatedAt = clock.instant();
        return buildPluginConfig();
    }

    public synchronized OcsPluginConfig getPluginConfig(String sessionId) {
        requireAvailable();
        pruneExpired();
        requireSession(sessionId);
        return buildPluginConfig();
    }

    public synchronized OcsStudySession reportPluginConfigApplied(
            String sessionId,
            boolean applied,
            List<String> missingKeys) {
        requireAvailable();
        SessionState state = requireSession(sessionId);
        requireNonTerminal(state);
        if (state.pluginStatus != null) {
            state.pluginStatus = new OcsPluginStatus(
                    state.pluginStatus.detected(),
                    state.pluginStatus.version(),
                    applied,
                    missingKeys == null ? List.of() : List.copyOf(missingKeys));
        }
        state.lastEvent = applied ? "OCS 插件配置已生效" : "OCS 插件配置部分缺失";
        state.updatedAt = clock.instant();
        return state.snapshot();
    }

    public synchronized OcsStudySession reportLoginQr(
            String sessionId,
            String qrDataUrl,
            String pageUrl) {
        requireAvailable();
        SessionState state = requireSession(sessionId);
        requireNonTerminal(state);
        if (!properties.isLoginQrRelayEnabled()) {
            throw new IllegalArgumentException("OCS 登录二维码中继未启用");
        }
        Instant now = clock.instant();
        state.loginQrRelay = new OcsLoginQrRelay(
                qrDataUrl,
                now.plus(properties.getLoginQrTimeout()));
        state.status = Status.WAITING_LOGIN;
        state.lastEvent = "已获取登录二维码，等待用户扫码";
        state.updatedAt = now;
        return state.snapshot();
    }

    public synchronized OcsStudySession reportLoginConfirmed(String sessionId) {
        requireAvailable();
        SessionState state = requireSession(sessionId);
        requireNonTerminal(state);
        state.loginQrRelay = null;
        state.status = Status.DISCOVERING;
        state.lastEvent = "登录已确认，继续课程";
        state.updatedAt = clock.instant();
        return state.snapshot();
    }

    public synchronized OcsStudySession reportPluginMissing(
            String sessionId,
            String reason) {
        requireAvailable();
        SessionState state = requireSession(sessionId);
        requireNonTerminal(state);
        String safeReason = boundedOptionalText(reason, 300, "原因过长");
        state.status = Status.PLUGIN_MISSING;
        state.pluginStatus = new OcsPluginStatus(false, "", false, List.of());
        state.lastEvent = "PLUGIN_MISSING：" + safeReason;
        LOG.warn("ocs_plugin_missing sessionId={} reason={}", sessionId, safeReason);
        state.updatedAt = clock.instant();
        return state.snapshot();
    }

    public OcsLoginQrRelay getLoginQrRelay(String sessionId) {
        requireAvailable();
        SessionState state = sessionMap.get(sessionId);
        if (state == null || state.loginQrRelay == null) {
            return null;
        }
        if (state.loginQrRelay.expiresAt().isBefore(clock.instant())) {
            state.loginQrRelay = null;
            return null;
        }
        return state.loginQrRelay;
    }

    private OcsPluginConfig buildPluginConfig() {
        return new OcsPluginConfig(
                properties.isOcsConfigVideoReview(),
                properties.isOcsConfigAutoChapterSwitch(),
                properties.getOcsConfigPlaybackRate(),
                properties.isOcsConfigAnswerEnabled(),
                properties.getOcsConfigQuestionBankUrl(),
                !properties.getOcsConfigQuestionBankUrl().isBlank());
    }

    private void requireAvailable() {
        if (!available()) {
            throw new IllegalArgumentException("OCS 本机桥接未启用或配置不完整");
        }
    }

    private String requireConversation(String conversationId) {
        String owner = requireBoundedText(conversationId, "会话身份不能为空", 256);
        var restrictedConversationList = properties.getAllowedConversationList().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
        if (!restrictedConversationList.isEmpty() && !restrictedConversationList.contains(owner)) {
            throw new IllegalArgumentException("当前会话没有使用 OCS 学习能力的权限");
        }
        return owner;
    }

    private URI normalizeCourseUrl(String rawCourseUrl) {
        URI uri = parseUrl(rawCourseUrl, "课程网址不能为空");
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("课程网址必须是无用户信息的 HTTPS 网址");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        var restrictedHostList = properties.getAllowedUrlHostList().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .toList();
        if (!restrictedHostList.isEmpty() && !restrictedHostList.contains(host)) {
            throw new IllegalArgumentException("课程网址主机不在 OCS 允许列表中");
        }
        return uri;
    }

    private static String normalizeCourseReference(String courseReference) {
        if (courseReference == null || courseReference.isBlank()) {
            return "";
        }
        String normalized = courseReference.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches(COURSE_REFERENCE_PATTERN)) {
            throw new IllegalArgumentException("课程引用必须是 C1 至 C999");
        }
        return normalized;
    }

    private ResolvedCourse resolveTargetCourse(
            String conversationId,
            String courseReference,
            String courseName) {
        if (courseReferenceResolver == null) {
            if (courseReference.isBlank()) {
                return null;
            }
            throw new IllegalArgumentException("当前无法解析课程引用，请改用完整课程名称");
        }
        if (courseReference.isBlank()) {
            return courseReferenceResolver
                    .resolveLatestSelection(conversationId, courseName)
                    .orElse(null);
        }
        return new ResolvedCourse(
                courseReference,
                courseReferenceResolver.resolve(
                        conversationId,
                        courseReference,
                        courseName));
    }

    private URI parseUrl(String rawUrl, String blankMessage) {
        String value = requireBoundedText(rawUrl, blankMessage, MAX_URL_CHARACTERS);
        try {
            return URI.create(value).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("网址格式不正确", exception);
        }
    }

    private SessionState requireSession(String sessionId) {
        String safeId = requireBoundedText(sessionId, "OCS 学习会话 ID 不能为空", 64);
        SessionState state = sessionMap.get(safeId);
        if (state == null) {
            throw new IllegalArgumentException("未找到 OCS 学习会话");
        }
        return state;
    }

    private void pruneExpired() {
        Instant expiryThreshold = clock.instant().minus(properties.getSessionExpireAfter());
        Iterator<Map.Entry<String, SessionState>> iterator = sessionMap.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().updatedAt.isBefore(expiryThreshold)) {
                iterator.remove();
            }
        }
    }

    private OcsStudySession markLaunchFailed(SessionState state, String message) {
        state.status = Status.FAILED;
        state.lastEvent = "FAILED：" + message;
        state.launchToken = "";
        state.updatedAt = clock.instant();
        return state.snapshot();
    }

    private String updatedNotes(
            String existingNotes,
            EventType eventType,
            String lessonTitle,
            String content) {
        String addition = switch (eventType) {
            case LESSON_CHANGED -> lessonTitle.isBlank() ? "" : "【章节】" + lessonTitle;
            case CAPTION, NOTE -> content;
            case QUESTION -> content.isBlank() ? "" : "【题目】" + content;
            default -> "";
        };
        if (addition.isBlank()) {
            return existingNotes;
        }
        return appendNote(existingNotes, addition);
    }

    private String appendNote(String existingNotes, String addition) {
        String combined = existingNotes.isBlank()
                ? addition
                : existingNotes + System.lineSeparator() + addition;
        int maxCharacters = properties.getMaxNotesCharacters();
        if (combined.length() <= maxCharacters) {
            return combined;
        }
        return "…" + combined.substring(combined.length() - maxCharacters + 1);
    }

    private static Status nextStatus(EventType eventType) {
        return switch (eventType) {
            case LOGIN_REQUIRED -> Status.WAITING_LOGIN;
            case DISCOVERING -> Status.DISCOVERING;
            case QUESTION -> Status.WAITING_QUESTION;
            case PAUSED -> Status.PAUSED;
            case FINISHED -> Status.SUCCEEDED;
            case FAILED -> Status.FAILED;
            case STARTED, HEARTBEAT, LESSON_CHANGED, CAPTION, NOTE, PROGRESS -> Status.RUNNING;
        };
    }

    private static String eventSummary(EventType eventType, String content) {
        if (eventType == EventType.FAILED && !content.isBlank()) {
            return eventType.name() + "：" + content;
        }
        return eventType.name();
    }

    private static EventType parseEventType(String rawEventType) {
        String value = requireBoundedText(rawEventType, "OCS 事件类型不能为空", 32)
                .toUpperCase(Locale.ROOT);
        try {
            return EventType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的 OCS 事件类型", exception);
        }
    }

    private static AnswerResult parseAnswerResult(String rawResult) {
        String value = requireBoundedText(rawResult, "答题结果不能为空", 32)
                .toUpperCase(Locale.ROOT);
        try {
            return AnswerResult.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的答题结果", exception);
        }
    }

    private static void requireNonTerminal(SessionState state) {
        if (isTerminal(state.status)) {
            throw new IllegalArgumentException("OCS 学习会话已经结束");
        }
    }

    private static boolean isTerminal(Status status) {
        return status == Status.SUCCEEDED || status == Status.FAILED;
    }

    private static String requireBoundedText(String value, String blankMessage, int maxCharacters) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(blankMessage);
        }
        String safeValue = value.strip();
        if (safeValue.length() > maxCharacters) {
            throw new IllegalArgumentException("输入内容过长");
        }
        return safeValue;
    }

    private static String boundedOptionalText(String value, int maxCharacters, String errorMessage) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String safeValue = value.strip();
        if (safeValue.length() > maxCharacters) {
            throw new IllegalArgumentException(errorMessage);
        }
        return safeValue;
    }

    private enum EventType {
        STARTED,
        HEARTBEAT,
        LOGIN_REQUIRED,
        DISCOVERING,
        LESSON_CHANGED,
        CAPTION,
        NOTE,
        QUESTION,
        PROGRESS,
        PAUSED,
        FINISHED,
        FAILED
    }

    private enum AnswerResult {
        SELECTED,
        SUBMITTED,
        CORRECT,
        WRONG,
        BLOCKED
    }

    private static final class SessionState {

        private final String sessionId;
        private final String conversationId;
        private final String courseName;
        private final String courseReference;
        private final String chapterName;
        private final URI courseUrl;
        private final URI targetCourseUrl;
        private final String commandId;
        private final Instant createdAt;
        private final Set<String> questionIds = new HashSet<>();
        private String currentQuestionId = "";
        private int answerAttempts;
        private String launchToken;
        private String leaseId;
        private Instant leaseExpiresAt;
        private boolean commandAcknowledged;
        private Status status = Status.CREATED;
        private int progressPercent;
        private String currentLesson = "";
        private String notes = "";
        private String lastEvent = "等待专用浏览器标签页领取任务";
        private OcsPluginStatus pluginStatus;
        private OcsLoginQrRelay loginQrRelay;
        private boolean companionConnected;
        private Instant updatedAt;

        private SessionState(
                String sessionId,
                String conversationId,
                String courseName,
                String courseReference,
                String chapterName,
                URI courseUrl,
                URI targetCourseUrl,
                String commandId,
                String launchToken,
                Instant createdAt) {
            this.sessionId = sessionId;
            this.conversationId = conversationId;
            this.courseName = courseName;
            this.courseReference = courseReference;
            this.chapterName = chapterName;
            this.courseUrl = courseUrl;
            this.targetCourseUrl = targetCourseUrl;
            this.commandId = commandId;
            this.launchToken = launchToken;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
        }

        private SessionState(
                String sessionId,
                String conversationId,
                String courseName,
                String courseReference,
                String chapterName,
                URI courseUrl,
                URI targetCourseUrl,
                Instant createdAt) {
            this.sessionId = sessionId;
            this.conversationId = conversationId;
            this.courseName = courseName;
            this.courseReference = courseReference;
            this.chapterName = chapterName;
            this.courseUrl = courseUrl;
            this.targetCourseUrl = targetCourseUrl;
            this.commandId = "";
            this.launchToken = "";
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
        }

        private OcsStudySession snapshot() {
            return new OcsStudySession(
                    sessionId,
                    courseName,
                    chapterName,
                    status,
                    progressPercent,
                    currentLesson,
                    notes,
                    lastEvent,
                    pluginStatus,
                    createdAt,
                    updatedAt);
        }
    }
}
