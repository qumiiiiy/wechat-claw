package com.weida.bot.wechatilinkbot.agent;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.SEND_EMAIL;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.SELECT_FILE;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.CURRENT_WEATHER;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.CANCEL_DESKTOP_TASK;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.CONFIRM_DESKTOP_TASK;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GET_DESKTOP_TASK_STATUS;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GET_OCS_STUDY_STATUS;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.REQUEST_OPEN_URL;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.START_OCS_STUDY;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.ADD_TODO;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.COMPLETE_TODO;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.LIST_TODOS;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.CANCEL_TODO;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.DAILY_SUMMARY;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.BROWSE_FILES;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.MODIFY_TODO;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.QUERY_TODOS;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.TODAY_TODOS;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DIAGRAM;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DOCUMENT;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_IMAGE;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_PRESENTATION;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_QR_CODE;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_SPREADSHEET;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_TEXT_FILE;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.READ_SPREADSHEET;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.APPEND_SPREADSHEET;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.LEARNING_QUERY;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.agent.intent.DesktopTaskOperation;
import com.weida.bot.wechatilinkbot.agent.intent.OcsStudyOperation;
import com.weida.bot.wechatilinkbot.agent.intent.SpreadsheetOperation;
import com.weida.bot.wechatilinkbot.agent.intent.TodoOperation;
import com.weida.bot.wechatilinkbot.model.AgentReply;
import com.weida.bot.wechatilinkbot.model.AgentRequest;
import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import com.weida.bot.wechatilinkbot.model.MediaPart;
import com.weida.bot.wechatilinkbot.capability.EmotionalProfileService;
import com.weida.bot.wechatilinkbot.persistence.ConversationMemory;
import com.weida.bot.wechatilinkbot.persistence.ConversationMemoryProperties;
import com.weida.bot.wechatilinkbot.persistence.GeneratedImageMemory;
import com.weida.bot.wechatilinkbot.persistence.GeneratedFileMemory;
import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import com.weida.bot.wechatilinkbot.tool.ToolInvocation;
import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import com.weida.bot.wechatilinkbot.tool.ToolRunContext;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Application-level conversational agent.
 *
 * <p>This class coordinates memory, intent routing, provider-neutral model calls, tools,
 * fallbacks, and persistence. Channel and provider SDK types cannot enter this module.</p>
 */
@Component
public class AgentRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(AgentRuntime.class);
    private static final int MAX_GENERATED_MEDIA = 2;
    private static final Pattern INBOUND_FILE_NAME_PATTERN = Pattern.compile(
            "[“\\\"]([^“”\\\"\\r\\n]{1,160}\\.(?:docx?|pdf|xlsx?|pptx?|txt|md|csv|json|png|jpe?g|gif|webp))[”\\\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COURSE_REFERENCE_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9])C([1-9][0-9]{0,2})(?![A-Za-z0-9])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_COURSE_NAME_PATTERN = Pattern.compile(
            "[《“「\\\"]([^》”」\\\"]{1,80})[》”」\\\"]");
    private static final Pattern COURSE_NAME_TASK_PATTERN = Pattern.compile(
            "(?:查询|查看|检查|查找|列出|获取|看看|查)?\\s*"
                    + "(?:学习通|超星)?\\s*"
                    + "([\\p{L}\\p{N}][^，。；：:！？!?]{0,80}?)(?:的)?"
                    + "(?:课程)?(?:任务|作业)(?:列表)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEARNING_COMPLETION_PATTERN = Pattern.compile(
            "(?:标记(?:为)?完成|批量完成|全部完成|完成所有|自动完成|批量标记)");
    private static final String EMAIL_TOOL_UNAVAILABLE_REPLY =
            "邮件未发送：邮件发送功能当前未启用或配置不完整。";

    private final AgentProperties properties;
    private final AgentModelClient modelClient;
    private final ConversationCoordinator coordinator;
    private final ConversationMemory conversationMemory;
    private final GeneratedImageMemory generatedImageMemory;
    private final GeneratedFileMemory generatedFileMemory;
    private final EmotionalProfileService emotionalProfileService;
    private final AgentIntentResolver intentResolver;
    private final AgentPromptAssembler promptAssembler;
    private final AgentToolLoop toolLoop;
    private final AgentResponsePolicy responsePolicy;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final ConversationContextCompactor contextCompactor;
    private final ConversationReferenceStore referenceStore;
    private final AgentUserOutputRenderer outputRenderer;

    @Autowired
    public AgentRuntime(
            AgentProperties properties,
            AgentModelClient modelClient,
            ConversationCoordinator coordinator,
            ConversationMemory conversationMemory,
            GeneratedImageMemory generatedImageMemory,
            GeneratedFileMemory generatedFileMemory,
            EmotionalProfileService emotionalProfileService,
            AgentIntentResolver intentResolver,
            AgentPromptAssembler promptAssembler,
            AgentToolLoop toolLoop,
            AgentResponsePolicy responsePolicy,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            ConversationContextCompactor contextCompactor,
            ConversationReferenceStore referenceStore,
            AgentUserOutputRenderer outputRenderer) {
        this.properties = properties;
        this.modelClient = modelClient;
        this.coordinator = coordinator;
        this.conversationMemory = conversationMemory;
        this.generatedImageMemory = generatedImageMemory;
        this.generatedFileMemory = generatedFileMemory;
        this.emotionalProfileService = emotionalProfileService;
        this.intentResolver = intentResolver;
        this.promptAssembler = promptAssembler;
        this.toolLoop = toolLoop;
        this.responsePolicy = responsePolicy;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.contextCompactor = contextCompactor;
        this.referenceStore = referenceStore;
        this.outputRenderer = outputRenderer;
    }

    public AgentRuntime(
            AgentProperties properties,
            AgentModelClient modelClient,
            ConversationCoordinator coordinator,
            ConversationMemory conversationMemory,
            GeneratedImageMemory generatedImageMemory,
            EmotionalProfileService emotionalProfileService,
            AgentIntentResolver intentResolver,
            AgentPromptAssembler promptAssembler,
            AgentToolLoop toolLoop,
            AgentResponsePolicy responsePolicy,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper) {
        this(
                properties,
                modelClient,
                coordinator,
                conversationMemory,
                generatedImageMemory,
                new GeneratedFileMemory(new com.weida.bot.wechatilinkbot.persistence.GeneratedFileMemoryProperties()),
                emotionalProfileService,
                intentResolver,
                promptAssembler,
                toolLoop,
                responsePolicy,
                toolRegistry,
                objectMapper,
                new ConversationContextCompactor(
                        properties,
                        modelClient,
                        conversationMemory,
                        new ConversationMemoryProperties()),
                new ConversationReferenceStore(properties));
    }

    AgentRuntime(
            AgentProperties properties,
            AgentModelClient modelClient,
            ConversationCoordinator coordinator,
            ConversationMemory conversationMemory,
            GeneratedImageMemory generatedImageMemory,
            GeneratedFileMemory generatedFileMemory,
            EmotionalProfileService emotionalProfileService,
            AgentIntentResolver intentResolver,
            AgentPromptAssembler promptAssembler,
            AgentToolLoop toolLoop,
            AgentResponsePolicy responsePolicy,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            ConversationContextCompactor contextCompactor,
            ConversationReferenceStore referenceStore) {
        this(
                properties,
                modelClient,
                coordinator,
                conversationMemory,
                generatedImageMemory,
                generatedFileMemory,
                emotionalProfileService,
                intentResolver,
                promptAssembler,
                toolLoop,
                responsePolicy,
                toolRegistry,
                objectMapper,
                contextCompactor,
                referenceStore,
                new AgentUserOutputRenderer(objectMapper, referenceStore));
    }

    public boolean available() {
        return modelClient.available();
    }

    public int maxMediaBytes() {
        return properties.getMaxMediaBytes();
    }

    public Optional<AgentReply> reply(AgentRequest request) {
        if (!available()) {
            return Optional.empty();
        }
        return Optional.of(coordinator.execute(
                request.conversationId(),
                () -> run(request)));
    }

    public void clearConversation(String conversationId) {
        coordinator.execute(conversationId, () -> {
            conversationMemory.clear(conversationId);
            generatedImageMemory.clear(conversationId);
            generatedFileMemory.clear(conversationId);
            toolRegistry.clearConversation(conversationId);
            referenceStore.clear(conversationId);
            LOG.info(
                    "agent_conversation_cleared conversation={}",
                    conversationReference(conversationId));
            return null;
        });
    }

    private AgentReply run(AgentRequest request) throws IOException {
        long startNanos = System.nanoTime();
        String conversationReference = conversationReference(request.conversationId());
        List<ConversationMessage> history = contextCompactor.load(request.conversationId());
        List<MediaPart> acceptedMedia = acceptedMedia(request.mediaParts());
        AgentIntent intent = intentResolver.resolve(history, request);
        List<MediaPart> effectiveMedia = effectiveMedia(
                request.conversationId(),
                acceptedMedia);
        LOG.info(
                "agent_run_started conversation={} promptLength={} historyMessages={} "
                        + "userTextLength={} mediaParts={} speechRequested={} imageRequested={} "
                        + "textFileRequested={} spreadsheetRequested={} "
                        + "emailRequested={} voicePreference={} fileBrowseRequested={} "
                        + "presentationRequested={}",
                conversationReference,
                request.prompt().length(),
                history.size(),
                request.currentUserText().length(),
                effectiveMedia.size(),
                intent.speechRequested(),
                intent.imageRequested(),
                intent.textFileRequested(),
                intent.spreadsheetRequested(),
                intent.emailSendRequested(),
                intent.voicePreference(),
                intent.fileBrowseRequested(),
                intent.presentationRequested());
        if (intent.emailSendRequested() && !toolRegistry.available(SEND_EMAIL)) {
            LOG.warn(
                    "agent_email_tool_unavailable conversation={} reason={}",
                    conversationReference,
                    toolRegistry.availabilityReason(SEND_EMAIL));
            return finish(
                    request,
                    new AgentReply(
                            AgentCapabilityReadiness.unavailableReply(
                                    toolRegistry,
                                    SEND_EMAIL,
                                    EMAIL_TOOL_UNAVAILABLE_REPLY),
                            List.of()),
                    startNanos,
                    conversationReference);
        }
        if (intent.fileBrowseRequested() && !toolRegistry.available(BROWSE_FILES)) {
            LOG.warn(
                    "agent_file_browse_tool_unavailable conversation={} reason={}",
                    conversationReference,
                    toolRegistry.availabilityReason(BROWSE_FILES));
            return finish(
                    request,
                    new AgentReply(
                            AgentCapabilityReadiness.unavailableReply(
                                    toolRegistry,
                                    BROWSE_FILES,
                                    "文件搜索当前不可用。"),
                            List.of()),
                    startNanos,
                    conversationReference);
        }
        if (isLearningCompletionRequest(request.currentUserText())
                && !hasLearningReadVerb(request.currentUserText())) {
            return finish(
                    request,
                    new AgentReply(
                            "学习通课程任务是只读数据，不能在这里标记完成或批量完成。"
                                    + "我可以查询真实进度；如需修改，请在学习通页面完成，"
                                    + "个人待办只能单独处理。",
                            List.of()),
                    startNanos,
                    conversationReference);
        }
        if (isConcreteLearningRequest(request.currentUserText())
                && intent.learningQueryRequested()
                && isLearningOnlyIntent(intent)
                && !toolRegistry.available(LEARNING_QUERY)) {
            LOG.warn(
                    "agent_learning_tool_unavailable conversation={} reason={}",
                    conversationReference,
                    toolRegistry.availabilityReason(LEARNING_QUERY));
            return finish(
                    request,
                    new AgentReply(
                            AgentCapabilityReadiness.unavailableReply(
                                    toolRegistry,
                                    LEARNING_QUERY,
                                    "学习通查询当前不可用。"),
                            List.of()),
                    startNanos,
                    conversationReference);
        }
        if (intent.ocsStudyOperation() != OcsStudyOperation.NONE) {
            String ocsTool = ocsToolName(intent.ocsStudyOperation());
            if (!toolRegistry.available(ocsTool)) {
                LOG.warn(
                        "agent_ocs_tool_unavailable conversation={} reason={}",
                        conversationReference,
                        toolRegistry.availabilityReason(ocsTool));
                return finish(
                        request,
                        new AgentReply(
                                AgentCapabilityReadiness.unavailableReply(
                                        toolRegistry,
                                        ocsTool,
                                        "OCS 学习当前不可用。"),
                                List.of()),
                        startNanos,
                        conversationReference);
            }
        }
        if (intent.presentationRequested() && !toolRegistry.available(GENERATE_PRESENTATION)) {
            LOG.warn(
                    "agent_presentation_tool_unavailable conversation={} reason={}",
                    conversationReference,
                    toolRegistry.availabilityReason(GENERATE_PRESENTATION));
            return finish(
                    request,
                    new AgentReply(
                            AgentCapabilityReadiness.unavailableReply(
                                    toolRegistry,
                                    GENERATE_PRESENTATION,
                                    "PPTX 演示文稿当前不可用。"),
                            List.of()),
                    startNanos,
                    conversationReference);
        }
        if (intent.diagramRequested() && !toolRegistry.available(GENERATE_DIAGRAM)) {
            LOG.warn(
                    "agent_diagram_tool_unavailable conversation={} reason={}",
                    conversationReference,
                    toolRegistry.availabilityReason(GENERATE_DIAGRAM));
            return finish(
                    request,
                    new AgentReply(
                            AgentCapabilityReadiness.unavailableReply(
                                    toolRegistry,
                                    GENERATE_DIAGRAM,
                                    "PlantUML 图表当前不可用。"),
                            List.of()),
                    startNanos,
                    conversationReference);
        }
        String unavailableExplicitTool = firstUnavailableExplicitTool(intent);
        if (unavailableExplicitTool != null) {
            LOG.warn(
                    "agent_explicit_tool_unavailable conversation={} tool={} reason={}",
                    conversationReference,
                    unavailableExplicitTool,
                    toolRegistry.availabilityReason(unavailableExplicitTool));
            return finish(
                    request,
                    new AgentReply(
                            AgentCapabilityReadiness.unavailableReply(
                                    toolRegistry,
                                    unavailableExplicitTool,
                                    unavailableFallback(unavailableExplicitTool)),
                            List.of()),
                    startNanos,
                    conversationReference);
        }
        if (intent.todoOperation() == TodoOperation.MULTIPLE) {
            return finish(
                    request,
                    new AgentReply(
                            "检测到一条消息中包含多个待办操作。为避免漏建或查错，请按顺序分别发送："
                                    + "先新增待办，再查询明天任务，最后发送“今日要务”。",
                            List.of()),
                    startNanos,
                    conversationReference);
        }
        AgentReply reply = complete(
                request,
                history,
                effectiveMedia,
                intent,
                conversationReference);
        return finish(request, reply, startNanos, conversationReference);
    }

    private AgentReply complete(
            AgentRequest request,
            List<ConversationMessage> history,
            List<MediaPart> mediaParts,
            AgentIntent intent,
            String conversationReference) throws IOException {
        Optional<ConversationReferenceStore.Reference> naturalFileReference =
                naturalReference(request, ConversationReferenceStore.ReferenceType.FILE);
        boolean preselectedFileForEmail = intent.fileSelectionRequested()
                && intent.emailSendRequested()
                && naturalFileReference.isPresent()
                && extractExplicitFileSelectionId(request.currentUserText()).isBlank();
        // The model owns semantic tool selection and ordering. The registry still filters
        // disabled capabilities and the speech tool, while each tool/capability enforces its
        // own server-side authorization and side-effect policy.
        List<ToolDefinition> definitions = toolRegistry.listModelDefinitions(true);
        List<String> requiredToolNames = requiredToolNamesForRequest(intent);
        int mediaLimit = intent.speechRequested()
                ? MAX_GENERATED_MEDIA - 1
                : MAX_GENERATED_MEDIA;
        ToolRunContext runContext = new ToolRunContext(
                request.conversationId(),
                request.currentUserText(),
                intent.emailSendRequested(),
                intent.weatherInformationRequested(),
                intent.emailAttachmentRequested(),
                generatedFileMemory.getLatest(request.conversationId()).stream().toList(),
                recentInboundFileNames(history, request),
                mediaLimit,
                objectMapper);
        String directTodoTool = directTodoToolName(intent.todoOperation());
        if (directTodoTool != null && toolRegistry.available(directTodoTool)) {
            ToolInvocation invocation = toolRegistry.execute(
                    directTodoTool,
                    "{}",
                    runContext);
            return new AgentReply(invocation.content(), List.of());
        }
        AgentReply directReferenceReply = executeNaturalReferenceAction(
                request,
                intent,
                runContext);
        if (directReferenceReply != null) {
            return directReferenceReply;
        }
        AgentReply directLearningReply = executeConcreteLearningRequest(
                request,
                intent,
                runContext);
        if (directLearningReply != null) {
            return directLearningReply;
        }
        if (preselectedFileForEmail && toolRegistry.available(SELECT_FILE)) {
            String fileId = naturalFileReference.orElseThrow().identifier();
            ToolInvocation invocation = toolRegistry.execute(
                    SELECT_FILE,
                    objectMapper.createObjectNode().put("fileId", fileId).toString(),
                    runContext);
            if (!invocation.succeeded()) {
                return new AgentReply(invocation.content(), runContext.generatedMedia());
            }
            runContext.suppressLatestGeneratedMediaDelivery();
        }
        if (intent.fileSelectionRequested()
                && !intent.emailSendRequested()
                && toolRegistry.available(SELECT_FILE)) {
            String fileId = extractFileSelectionId(
                    request.conversationId(),
                    request.currentUserText());
            if (fileId.isBlank()) {
                return new AgentReply(
                        "请先搜索文件，再说“把第一个发给我”或“把刚才的文件发给我”。",
                        List.of());
            }
            ToolInvocation invocation = toolRegistry.execute(
                    SELECT_FILE,
                    objectMapper.createObjectNode().put("fileId", fileId).toString(),
                    runContext);
            return new AgentReply(invocation.content(), runContext.generatedMedia());
        }
        AgentRequest modelRequest = requestWithReferenceHints(request);
        if (request.mediaParts().isEmpty() && !mediaParts.isEmpty()) {
            modelRequest = withRememberedImageHint(modelRequest);
        }
        List<ModelMessage> messages = promptAssembler.assemble(
                history,
                modelRequest,
                mediaParts,
                intent.speechRequested());
        LOG.info(
                "agent_model_request_started conversation={} messages={} tools={} "
                        + "requiredTools={}",
                conversationReference,
                messages.size(),
                definitions.size(),
                requiredToolNames);
        String content = toolLoop.complete(
                messages,
                definitions,
                requiredToolNames,
                runContext,
                conversationReference);
        return responsePolicy.finish(
                content,
                runContext,
                intent,
                conversationReference);
    }

    /**
     * Executes a concrete learning request on the server-owned route.
     *
     * <p>Course lists, login challenges, and task queries are read-only but still depend on
     * authenticated upstream state. Allowing the model to answer these turns without invoking
     * {@code learning_query} would let it repeat stale history as if it were a fresh result.
     * Combinations such as “查课程后发邮件” remain model-planned so the existing multi-tool
     * workflow can collect the additional information first.</p>
     */
    private AgentReply executeConcreteLearningRequest(
            AgentRequest request,
            AgentIntent intent,
            ToolRunContext runContext) {
        if (!isConcreteLearningRequest(request.currentUserText())
                || !intent.learningQueryRequested()
                || !isLearningRouteAllowed(intent, request.currentUserText())) {
            return null;
        }
        if (!toolRegistry.available(LEARNING_QUERY)) {
            return null;
        }
        ToolInvocation invocation = toolRegistry.execute(
                LEARNING_QUERY,
                learningArguments(request),
                runContext);
        String content = invocation.content();
        if (invocation.succeeded()
                && hasLearningTodoMutationRequest(request.currentUserText())) {
            content = appendLearningReadOnlyNote(content, request.currentUserText());
        }
        return new AgentReply(content, runContext.generatedMedia());
    }

    private String learningArguments(AgentRequest request) {
        String normalized = normalizeUserText(request.currentUserText());
        ObjectNode arguments = objectMapper.createObjectNode();
        String action = learningAction(normalized);
        arguments.put("action", action);
        Optional<ConversationReferenceStore.Reference> courseReference = referenceStore.resolve(
                request.conversationId(),
                ConversationReferenceStore.ReferenceType.COURSE,
                request.currentUserText());
        String explicitCourseReference = extractCourseReference(normalized);
        if (!explicitCourseReference.isBlank()) {
            arguments.put("courseReference", explicitCourseReference);
        } else if ("tasks".equals(action)) {
            String courseName = extractCourseName(normalized);
            if (!courseName.isBlank()) {
                arguments.put("action", "tasks_by_name");
                arguments.put("courseName", courseName);
            } else if (courseReference.isPresent()) {
                arguments.put("courseReference", courseReference.orElseThrow().identifier());
            }
        } else if (courseReference.isPresent() && !"courses".equals(action)) {
            arguments.put("courseReference", courseReference.orElseThrow().identifier());
        }
        return arguments.toString();
    }

    private static String learningAction(String normalizedText) {
        if (containsAny(normalizedText, "登录", "扫码", "二维码", "重新登录", "换号")) {
            return "login";
        }
        if (containsAny(normalizedText, "未完成任务", "所有课程的任务", "全部课程任务", "课程待办")) {
            return "pending";
        }
        if (containsAny(normalizedText, "课程详情", "课程详细", "详细信息", "班级", "任课教师", "学生人数")) {
            return "details";
        }
        if (containsAny(normalizedText, "作业", "任务", "未交", "没做", "截止", "deadline")) {
            return "tasks";
        }
        return "courses";
    }

    private static String extractCourseReference(String normalizedText) {
        Matcher matcher = COURSE_REFERENCE_PATTERN.matcher(normalizedText);
        return matcher.find() ? "C" + matcher.group(1) : "";
    }

    private static boolean isConcreteLearningRequest(String text) {
        String normalized = normalizeUserText(text);
        return containsAny(
                normalized,
                "学习通",
                "超星",
                "chaoxing",
                "xuexitong",
                "查课程",
                "查看课程",
                "查询课程",
                "课程列表",
                "课程详情",
                "登录二维码",
                "登录学习通",
                "课程任务",
                "任务列表",
                "作业列表",
                "任务查询",
                "作业任务",
                "未完成任务")
                || COURSE_REFERENCE_PATTERN.matcher(normalized).find();
    }

    private static boolean isLearningOnlyIntent(AgentIntent intent) {
        return intent.ocsStudyOperation() == OcsStudyOperation.NONE
                && !intent.emailSendRequested()
                && !intent.fileBrowseRequested()
                && !intent.fileSelectionRequested()
                && !intent.spreadsheetRequested()
                && !intent.textFileRequested()
                && !intent.documentRequested()
                && !intent.presentationRequested()
                && !intent.imageRequested()
                && !intent.speechRequested()
                && !intent.qrCodeRequested()
                && !intent.diagramRequested()
                && intent.desktopTaskOperation() == DesktopTaskOperation.NONE
                && intent.todoOperation() == TodoOperation.NONE;
    }

    private static boolean isLearningRouteAllowed(AgentIntent intent, String userText) {
        if (isLearningOnlyIntent(intent)) {
            return true;
        }
        return intent.ocsStudyOperation() == OcsStudyOperation.NONE
                && !intent.emailSendRequested()
                && !intent.fileBrowseRequested()
                && !intent.fileSelectionRequested()
                && !intent.spreadsheetRequested()
                && !intent.textFileRequested()
                && !intent.documentRequested()
                && !intent.presentationRequested()
                && !intent.imageRequested()
                && !intent.speechRequested()
                && !intent.qrCodeRequested()
                && !intent.diagramRequested()
                && intent.desktopTaskOperation() == DesktopTaskOperation.NONE
                && hasLearningTodoMutationRequest(userText);
    }

    private static boolean hasLearningTodoMutationRequest(String text) {
        String normalized = normalizeUserText(text);
        return containsAny(
                normalized,
                "添加至待办",
                "加入待办",
                "添加到待办",
                "自动添加",
                "批量完成",
                "全部完成",
                "标记完成",
                "完成所有",
                "自动完成",
                "批量标记");
    }

    private static boolean isLearningCompletionRequest(String text) {
        String normalized = normalizeUserText(text);
        return isConcreteLearningRequest(normalized)
                && LEARNING_COMPLETION_PATTERN.matcher(normalized).find();
    }

    private static boolean hasLearningReadVerb(String text) {
        String normalized = normalizeUserText(text);
        return containsAny(normalized, "查询", "查看", "检查", "查找", "列出", "获取", "看看");
    }

    private static String appendLearningReadOnlyNote(String content, String userText) {
        String result = content == null ? "" : content.strip();
        String note = isLearningCompletionRequest(userText)
                ? "学习通课程任务是只读数据，不能标记完成或批量完成；请在学习通页面操作。"
                : "学习通课程任务是只读来源，不会被伪装成个人待办或写入学习通状态。"
                        + "需要个人提醒时，请单独创建个人待办并提供明确截止时间。";
        return result.isBlank() ? note : result + "\n\n" + note;
    }

    private static String extractCourseName(String normalizedText) {
        Matcher quoted = QUOTED_COURSE_NAME_PATTERN.matcher(normalizedText);
        if (quoted.find()) {
            String candidate = cleanCourseName(quoted.group(1));
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        Matcher task = COURSE_NAME_TASK_PATTERN.matcher(normalizedText);
        if (!task.find()) {
            return "";
        }
        return cleanCourseName(task.group(1));
    }

    private static String cleanCourseName(String candidate) {
        if (candidate == null) {
            return "";
        }
        String value = candidate.strip()
                .replaceFirst("^[的之]", "")
                .replaceFirst("[的之]$", "")
                .strip();
        return switch (value) {
            case "", "课程", "任务", "作业", "这门课", "这个课程", "所有课程", "全部课程",
                    "我的课程" -> "";
            default -> value;
        };
    }

    private static String ocsToolName(OcsStudyOperation operation) {
        return switch (operation) {
            case START -> START_OCS_STUDY;
            case STATUS -> GET_OCS_STUDY_STATUS;
            case NONE -> throw new IllegalArgumentException("没有对应的 OCS 工具");
        };
    }

    private static String normalizeUserText(String text) {
        return text == null
                ? ""
                : Normalizer.normalize(text.strip(), Normalizer.Form.NFKC)
                        .toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private AgentReply finish(
            AgentRequest request,
            AgentReply reply,
            long startNanos,
            String conversationReference) {
        String displayText = emotionalProfileService.extractAndUpdateProfile(
                request.conversationId(), reply.text());
        String renderedText = outputRenderer.render(
                request.conversationId(),
                displayText,
                reply.generatedMedia());
        AgentReply cleanedReply = new AgentReply(renderedText, reply.generatedMedia());
        generatedImageMemory.remember(request.conversationId(), cleanedReply.generatedMedia());
        generatedFileMemory.remember(request.conversationId(), cleanedReply.generatedMedia());
        if (properties.getHistoryMessages() > 0) {
            conversationMemory.saveExchange(
                    request.conversationId(),
                    promptAssembler.historyPrompt(request),
                    cleanedReply.text());
        }
        LOG.info(
                "agent_run_completed conversation={} replyLength={} generatedMedia={} "
                        + "elapsedMillis={}",
                conversationReference,
                cleanedReply.text().length(),
                cleanedReply.generatedMedia().size(),
                elapsedMillis(startNanos));
        return cleanedReply;
    }

    private List<MediaPart> acceptedMedia(List<MediaPart> mediaParts) {
        if (mediaParts == null || mediaParts.isEmpty()) {
            return List.of();
        }
        List<MediaPart> acceptedMedia = new ArrayList<>(mediaParts.size());
        for (MediaPart mediaPart : mediaParts) {
            if (mediaPart != null
                    && mediaPart.bytes().length <= properties.getMaxMediaBytes()) {
                acceptedMedia.add(mediaPart);
            }
        }
        return List.copyOf(acceptedMedia);
    }

    private List<String> recentInboundFileNames(
            List<ConversationMessage> history,
            AgentRequest request) {
        Set<String> names = new LinkedHashSet<>();
        collectInboundFileNames(request.prompt(), names);
        for (int index = history.size() - 1; index >= 0 && names.size() < 8; index--) {
            ConversationMessage message = history.get(index);
            if (message != null) {
                collectInboundFileNames(message.content(), names);
            }
        }
        return List.copyOf(names);
    }

    private static void collectInboundFileNames(String content, Set<String> names) {
        if (content == null || content.isBlank() || names.size() >= 8) {
            return;
        }
        Matcher matcher = INBOUND_FILE_NAME_PATTERN.matcher(content);
        while (matcher.find() && names.size() < 8) {
            String fileName = matcher.group(1).strip().replace('\\', '/');
            int separator = fileName.lastIndexOf('/');
            if (separator >= 0) {
                fileName = fileName.substring(separator + 1);
            }
            if (!fileName.isBlank()) {
                names.add(fileName);
            }
        }
    }

    private List<MediaPart> effectiveMedia(
            String conversationId,
            List<MediaPart> mediaParts) {
        if (!mediaParts.isEmpty()) {
            return mediaParts;
        }
        return generatedImageMemory.getLatest(conversationId)
                .map(List::of)
                .orElse(mediaParts);
    }

    private AgentRequest withRememberedImageHint(AgentRequest request) {
        return new AgentRequest(
                request.conversationId(),
                request.prompt()
                        + "\n\n[视觉上下文] 当前消息附带的图片是本会话最近生成的图片。"
                        + "只有在用户要求分析、修改或引用它时才使用，不要因图片本身改变用户当前任务。",
                request.currentUserText(),
                request.mediaParts());
    }

    private static String conversationReference(String conversationId) {
        return Integer.toUnsignedString(conversationId.hashCode(), 16);
    }

    private Optional<ConversationReferenceStore.Reference> naturalReference(
            AgentRequest request,
            ConversationReferenceStore.ReferenceType type) {
        return referenceStore.resolve(
                request.conversationId(),
                type,
                request.currentUserText());
    }

    private AgentRequest requestWithReferenceHints(AgentRequest request) {
        List<String> hints = new ArrayList<>();
        // Reference resolution is a server-owned identity boundary, not a semantic route.
        // Try every reference type so the model can recover when a short natural continuation
        // is missed by a detector; model hints contain the trusted identifier only internally.
        for (ConversationReferenceStore.ReferenceType type : List.of(
                ConversationReferenceStore.ReferenceType.FILE,
                ConversationReferenceStore.ReferenceType.TODO,
                ConversationReferenceStore.ReferenceType.DESKTOP_TASK,
                ConversationReferenceStore.ReferenceType.OCS_SESSION,
                ConversationReferenceStore.ReferenceType.COURSE)) {
            addReferenceHint(hints, request, type);
        }
        if (hints.isEmpty()) {
            return request;
        }
        return new AgentRequest(
                request.conversationId(),
                request.prompt() + "\n\n" + String.join("\n", hints),
                request.currentUserText(),
                request.mediaParts());
    }

    private void addReferenceHint(
            List<String> hints,
            AgentRequest request,
            ConversationReferenceStore.ReferenceType type) {
        String hint = referenceStore.modelHint(
                request.conversationId(),
                request.currentUserText(),
                type);
        if (!hint.isBlank()) {
            hints.add(hint);
        }
    }

    private AgentReply executeNaturalReferenceAction(
            AgentRequest request,
            AgentIntent intent,
            ToolRunContext runContext) {
        String toolName = null;
        String fieldName = null;
        ConversationReferenceStore.ReferenceType referenceType = null;
        if (intent.todoOperation() == TodoOperation.COMPLETE
                || intent.todoOperation() == TodoOperation.CANCEL) {
            toolName = todoToolName(intent.todoOperation());
            fieldName = "todoId";
            referenceType = ConversationReferenceStore.ReferenceType.TODO;
        } else if (intent.desktopTaskOperation() == DesktopTaskOperation.CONFIRM
                || intent.desktopTaskOperation() == DesktopTaskOperation.STATUS
                || intent.desktopTaskOperation() == DesktopTaskOperation.CANCEL) {
            toolName = desktopToolName(intent.desktopTaskOperation());
            fieldName = "taskId";
            referenceType = ConversationReferenceStore.ReferenceType.DESKTOP_TASK;
        } else if (intent.ocsStudyOperation() == OcsStudyOperation.STATUS) {
            toolName = GET_OCS_STUDY_STATUS;
            fieldName = "sessionId";
            referenceType = ConversationReferenceStore.ReferenceType.OCS_SESSION;
        } else if (intent.ocsStudyOperation() == OcsStudyOperation.START) {
            toolName = START_OCS_STUDY;
            referenceType = ConversationReferenceStore.ReferenceType.COURSE;
        }
        if (toolName == null || referenceType == null) {
            return null;
        }
        Optional<ConversationReferenceStore.Reference> reference = naturalReference(
                request,
                referenceType);
        if (reference.isEmpty() || !toolRegistry.available(toolName)) {
            return null;
        }
        ToolInvocation invocation = toolRegistry.execute(
                toolName,
                referenceArguments(toolName, fieldName, reference.orElseThrow()).toString(),
                runContext);
        return new AgentReply(invocation.content(), runContext.generatedMedia());
    }

    private List<String> requiredToolNamesForRequest(
            AgentIntent intent) {
        Set<String> required = new LinkedHashSet<>();
        if (intent.desktopTaskOperation() != DesktopTaskOperation.NONE) {
            required.add(desktopToolName(intent.desktopTaskOperation()));
        }
        if (intent.ocsStudyOperation() != OcsStudyOperation.NONE) {
            required.add(ocsToolName(intent.ocsStudyOperation()));
        }
        if (intent.fileSelectionRequested()) {
            required.add(SELECT_FILE);
        }
        if (intent.learningQueryRequested()) {
            required.add(LEARNING_QUERY);
        }
        if (intent.todoOperation() != TodoOperation.NONE
                && intent.todoOperation() != TodoOperation.MULTIPLE
                && directTodoToolName(intent.todoOperation()) == null) {
            required.add(todoToolName(intent.todoOperation()));
            if (intent.todoOperation() == TodoOperation.COMPLETE
                    || intent.todoOperation() == TodoOperation.CANCEL
                    || intent.todoOperation() == TodoOperation.POSTPONE) {
                required.add(LIST_TODOS);
            }
        }
        if (intent.fileBrowseRequested()) {
            required.add(BROWSE_FILES);
        }
        if (intent.spreadsheetOperation() == SpreadsheetOperation.READ) {
            required.add(READ_SPREADSHEET);
        } else if (intent.spreadsheetOperation() == SpreadsheetOperation.APPEND) {
            required.add(APPEND_SPREADSHEET);
        }
        if (intent.emailSendRequested()) {
            if (intent.resolvableWeatherRequested()) {
                required.add(CURRENT_WEATHER);
            }
            if (intent.spreadsheetRequested()) {
                required.add(GENERATE_SPREADSHEET);
            } else if (intent.textFileRequested()) {
                required.add(intent.documentRequested()
                        ? GENERATE_DOCUMENT
                        : GENERATE_TEXT_FILE);
            }
            required.add(SEND_EMAIL);
        }
        if (intent.spreadsheetRequested()) {
            required.add(GENERATE_SPREADSHEET);
        }
        if (intent.presentationRequested()) {
            required.add(GENERATE_PRESENTATION);
        }
        if (intent.diagramRequested()) {
            required.add(GENERATE_DIAGRAM);
        }
        if (intent.qrCodeRequested()) {
            required.add(GENERATE_QR_CODE);
        }
        if (intent.imageRequested()) {
            required.add(GENERATE_IMAGE);
        }
        if (intent.textFileRequested()) {
            required.add(intent.documentRequested()
                    ? GENERATE_DOCUMENT
                    : GENERATE_TEXT_FILE);
        }
        return required.stream()
                .filter(toolRegistry::available)
                .toList();
    }

    private String firstUnavailableExplicitTool(AgentIntent intent) {
        if (intent.desktopTaskOperation() != DesktopTaskOperation.NONE) {
            String toolName = desktopToolName(intent.desktopTaskOperation());
            if (!toolRegistry.available(toolName)) {
                return toolName;
            }
        }
        if (intent.fileSelectionRequested() && !toolRegistry.available(SELECT_FILE)) {
            return SELECT_FILE;
        }
        if (intent.learningQueryRequested() && !toolRegistry.available(LEARNING_QUERY)) {
            return LEARNING_QUERY;
        }
        if (!intent.learningQueryRequested()
                && intent.todoOperation() != TodoOperation.NONE
                && intent.todoOperation() != TodoOperation.MULTIPLE) {
            String toolName = todoToolName(intent.todoOperation());
            if (!toolRegistry.available(toolName)) {
                return toolName;
            }
        }
        if (intent.emailSendRequested()
                && intent.resolvableWeatherRequested()
                && !toolRegistry.available(CURRENT_WEATHER)) {
            return CURRENT_WEATHER;
        }
        String spreadsheetTool = spreadsheetToolName(intent);
        if (spreadsheetTool != null && !toolRegistry.available(spreadsheetTool)) {
            return spreadsheetTool;
        }
        if (intent.imageRequested() && !toolRegistry.available(GENERATE_IMAGE)) {
            return GENERATE_IMAGE;
        }
        if (intent.qrCodeRequested() && !toolRegistry.available(GENERATE_QR_CODE)) {
            return GENERATE_QR_CODE;
        }
        if (intent.textFileRequested()) {
            String fileTool = intent.documentRequested()
                    ? GENERATE_DOCUMENT
                    : GENERATE_TEXT_FILE;
            if (!toolRegistry.available(fileTool)) {
                return fileTool;
            }
        }
        return null;
    }

    private String spreadsheetToolName(AgentIntent intent) {
        if (intent.spreadsheetOperation() == SpreadsheetOperation.READ) {
            return READ_SPREADSHEET;
        }
        if (intent.spreadsheetOperation() == SpreadsheetOperation.APPEND) {
            return APPEND_SPREADSHEET;
        }
        return intent.spreadsheetRequested() ? GENERATE_SPREADSHEET : null;
    }

    private static String unavailableFallback(String toolName) {
        return switch (toolName) {
            case SELECT_FILE -> "文件选择当前不可用。";
            case CURRENT_WEATHER -> "实时天气查询当前不可用。";
            case GENERATE_IMAGE -> "图片生成当前不可用。";
            case GENERATE_QR_CODE -> "二维码生成当前不可用。";
            case GENERATE_TEXT_FILE -> "文本文件生成当前不可用。";
            case READ_SPREADSHEET, APPEND_SPREADSHEET -> "Excel 文件操作当前不可用。";
            default -> "这项能力当前不可用。";
        };
    }

    private ObjectNode referenceArguments(
            String toolName,
            String fieldName,
            ConversationReferenceStore.Reference reference) {
        ObjectNode arguments = objectMapper.createObjectNode();
        if (START_OCS_STUDY.equals(toolName)) {
            arguments.put("courseName", reference.label());
            arguments.put("courseReference", reference.identifier());
        } else {
            arguments.put(fieldName, reference.identifier());
        }
        return arguments;
    }

    private String extractFileSelectionId(String conversationId, String text) {
        Optional<ConversationReferenceStore.Reference> reference = referenceStore.resolve(
                conversationId,
                ConversationReferenceStore.ReferenceType.FILE,
                text);
        return reference.map(ConversationReferenceStore.Reference::identifier)
                .orElseGet(() -> extractExplicitFileSelectionId(text));
    }

    private static String extractExplicitFileSelectionId(String text) {
        if (text == null) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "file-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? matcher.group().toLowerCase(java.util.Locale.ROOT) : "";
    }

    private static String desktopToolName(DesktopTaskOperation operation) {
        return switch (operation) {
            case REQUEST_OPEN_URL -> REQUEST_OPEN_URL;
            case CONFIRM -> CONFIRM_DESKTOP_TASK;
            case STATUS -> GET_DESKTOP_TASK_STATUS;
            case CANCEL -> CANCEL_DESKTOP_TASK;
            case NONE -> throw new IllegalArgumentException("没有对应的电脑任务工具");
        };
    }

    private static String todoToolName(TodoOperation operation) {
        return switch (operation) {
            case LIST -> LIST_TODOS;
            case TODAY -> TODAY_TODOS;
            case ADD -> ADD_TODO;
            case COMPLETE -> COMPLETE_TODO;
            case CANCEL -> CANCEL_TODO;
            case QUERY -> QUERY_TODOS;
            case SUMMARY -> DAILY_SUMMARY;
            case POSTPONE -> MODIFY_TODO;
            case MULTIPLE, NONE -> throw new IllegalArgumentException("没有对应的单一待办工具");
        };
    }

    private static String directTodoToolName(TodoOperation operation) {
        return switch (operation) {
            case LIST -> LIST_TODOS;
            case TODAY -> TODAY_TODOS;
            case SUMMARY -> DAILY_SUMMARY;
            default -> null;
        };
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

}
