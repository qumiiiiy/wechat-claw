package com.weida.bot.wechatilinkbot.agent;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.CURRENT_WEATHER;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.BROWSE_FILES;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DIAGRAM;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_SPREADSHEET;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DOCUMENT;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_PRESENTATION;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GET_OCS_STUDY_STATUS;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.LEARNING_QUERY;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.SEND_EMAIL;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.REQUEST_OPEN_URL;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.SELECT_FILE;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.START_OCS_STUDY;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.DAILY_SUMMARY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.EmotionalProfileService;
import com.weida.bot.wechatilinkbot.agent.intent.DesktopTaskOperation;
import com.weida.bot.wechatilinkbot.agent.intent.OcsStudyOperation;
import com.weida.bot.wechatilinkbot.agent.intent.TodoOperation;
import com.weida.bot.wechatilinkbot.agent.intent.SpreadsheetOperation;
import com.weida.bot.wechatilinkbot.model.AgentReply;
import com.weida.bot.wechatilinkbot.model.AgentRequest;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.MediaKind;
import com.weida.bot.wechatilinkbot.model.MediaPart;
import com.weida.bot.wechatilinkbot.model.VoicePreference;
import com.weida.bot.wechatilinkbot.persistence.ConversationMemory;
import com.weida.bot.wechatilinkbot.persistence.GeneratedImageMemory;
import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import com.weida.bot.wechatilinkbot.tool.ToolExecution;
import com.weida.bot.wechatilinkbot.tool.ToolInvocation;
import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import com.weida.bot.wechatilinkbot.tool.ToolRunContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentRuntimeTest {

    private static final String CONVERSATION_ID = "wechat-ilink|user-1";

    private AgentModelClient modelClient;
    private ConversationMemory memory;
    private GeneratedImageMemory imageMemory;
    private EmotionalProfileService emotionalProfileService;
    private AgentIntentResolver intentResolver;
    private AgentPromptAssembler promptAssembler;
    private AgentToolLoop toolLoop;
    private AgentResponsePolicy responsePolicy;
    private ToolRegistry toolRegistry;
    private AgentRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        AgentProperties properties = new AgentProperties();
        modelClient = mock(AgentModelClient.class);
        when(modelClient.available()).thenReturn(true);
        memory = mock(ConversationMemory.class);
        when(memory.listRecent(anyString(), any(Integer.class))).thenReturn(List.of());
        imageMemory = mock(GeneratedImageMemory.class);
        intentResolver = mock(AgentIntentResolver.class);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(neutralIntent());
        promptAssembler = mock(AgentPromptAssembler.class);
        when(promptAssembler.assemble(
                anyList(),
                any(AgentRequest.class),
                anyList(),
                anyBoolean()))
                .thenReturn(List.of(ModelMessage.user("hello", List.of())));
        when(promptAssembler.historyPrompt(any(AgentRequest.class))).thenReturn("hello");
        toolLoop = mock(AgentToolLoop.class);
        when(toolLoop.complete(
                anyList(),
                anyList(),
                anyList(),
                any(),
                anyString()))
                .thenReturn("answer");
        responsePolicy = mock(AgentResponsePolicy.class);
        when(responsePolicy.finish(anyString(), any(), any(), anyString()))
                .thenReturn(new AgentReply("answer", List.of()));
        emotionalProfileService = mock(EmotionalProfileService.class);
        when(emotionalProfileService.extractAndUpdateProfile(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        toolRegistry = mock(ToolRegistry.class);
        when(toolRegistry.listModelDefinitions(anyBoolean())).thenReturn(List.of());
        runtime = new AgentRuntime(
                properties,
                modelClient,
                new ConversationCoordinator(properties),
                memory,
                imageMemory,
                emotionalProfileService,
                intentResolver,
                promptAssembler,
                toolLoop,
                responsePolicy,
                toolRegistry,
                new ObjectMapper());
    }

    @Test
    void completesOneRunAndPersistsTheExchange() {
        AgentReply reply = runtime.reply(request()).orElseThrow();

        assertThat(reply.text()).isEqualTo("answer");
        verify(memory).saveExchange(CONVERSATION_ID, "hello", "answer");
        verify(imageMemory).remember(CONVERSATION_ID, List.of());
    }

    @Test
    void includesTheLatestGeneratedImageInTheNextModelContext() {
        MediaPart rememberedImage = new MediaPart(
                MediaKind.IMAGE,
                "image/png",
                new byte[] {1, 2, 3});
        when(imageMemory.getLatest(CONVERSATION_ID))
                .thenReturn(java.util.Optional.of(rememberedImage));

        runtime.reply(request("把刚才的图片改成蓝色")).orElseThrow();

        verify(promptAssembler).assemble(
                anyList(),
                any(AgentRequest.class),
                eq(List.of(rememberedImage)),
                anyBoolean());
    }

    @Test
    void unavailableProviderReturnsEmptyWithoutTouchingMemory() {
        when(modelClient.available()).thenReturn(false);

        assertThat(runtime.reply(request())).isEmpty();

        verify(memory, never()).listRecent(anyString(), any(Integer.class));
    }

    @Test
    void clearConversationClearsEveryConversationOwnedStore() {
        runtime.clearConversation(CONVERSATION_ID);

        verify(memory).clear(CONVERSATION_ID);
        verify(imageMemory).clear(CONVERSATION_ID);
        verify(toolRegistry).clearConversation(CONVERSATION_ID);
    }

    @Test
    void plansWeatherBeforeTheRequiredEmailAction() throws Exception {
        AgentIntent emailWithWeatherIntent = new AgentIntent(
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                VoicePreference.DEFAULT);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(emailWithWeatherIntent);
        when(toolRegistry.available(CURRENT_WEATHER)).thenReturn(true);
        when(toolRegistry.available(SEND_EMAIL)).thenReturn(true);
        List<ToolDefinition> definitions = List.of(
                definition(CURRENT_WEATHER, false),
                definition(SEND_EMAIL, true));
        when(toolRegistry.listModelDefinitions(anyBoolean())).thenReturn(definitions);

        runtime.reply(request()).orElseThrow();

        verify(toolLoop).complete(
                anyList(),
                eq(definitions),
                eq(List.of(CURRENT_WEATHER, SEND_EMAIL)),
                any(),
                anyString());
    }

    @Test
    void exposesOnlyTheRequiredDesktopToolForAnExplicitOpenUrlIntent() throws Exception {
        AgentIntent desktopIntent = new AgentIntent(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.REQUEST_OPEN_URL);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class))).thenReturn(desktopIntent);
        when(toolRegistry.available(REQUEST_OPEN_URL)).thenReturn(true);
        List<ToolDefinition> definitions = List.of(definition(REQUEST_OPEN_URL, true));
        when(toolRegistry.listModelDefinitions(anyBoolean())).thenReturn(definitions);

        runtime.reply(request()).orElseThrow();

        verify(toolLoop).complete(
                anyList(),
                eq(definitions),
                eq(List.of(REQUEST_OPEN_URL)),
                any(),
                anyString());
    }

    @Test
    void exposesOnlyLearningToolForExplicitLearningIntent() throws Exception {
        AgentIntent learningIntent = new AgentIntent(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                OcsStudyOperation.NONE,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                false,
                TodoOperation.NONE);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class))).thenReturn(learningIntent);
        when(toolRegistry.available(LEARNING_QUERY)).thenReturn(true);
        List<ToolDefinition> definitions = List.of(definition(LEARNING_QUERY, false));
        when(toolRegistry.listModelDefinitions(anyBoolean())).thenReturn(definitions);

        runtime.reply(request()).orElseThrow();

        verify(toolLoop).complete(
                anyList(),
                eq(definitions),
                eq(List.of(LEARNING_QUERY)),
                any(),
                anyString());
    }

    @Test
    void executesConcreteLearningQueryBeforeAskingTheModel() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(learningIntent());
        when(toolRegistry.available(LEARNING_QUERY)).thenReturn(true);
        when(toolRegistry.execute(
                eq(LEARNING_QUERY),
                eq("{\"action\":\"courses\"}"),
                any()))
                .thenReturn(new ToolInvocation("📚 真实课程列表", true, true));

        AgentReply reply = runtime.reply(request("查课程")).orElseThrow();

        assertThat(reply.text()).contains("真实课程列表");
        verify(toolRegistry).execute(
                eq(LEARNING_QUERY),
                eq("{\"action\":\"courses\"}"),
                any());
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void returnsLearningToolFailureWithoutAllowingAStaleModelReply() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(learningIntent());
        when(toolRegistry.available(LEARNING_QUERY)).thenReturn(true);
        when(toolRegistry.execute(eq(LEARNING_QUERY), anyString(), any()))
                .thenReturn(new ToolInvocation("获取课程列表失败，请稍后重试。", true, false));

        AgentReply reply = runtime.reply(request("查课程")).orElseThrow();

        assertThat(reply.text()).contains("获取课程列表失败");
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void forwardsLearningLoginQrFromTheDirectToolRoute() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(learningIntent());
        when(toolRegistry.available(LEARNING_QUERY)).thenReturn(true);
        GeneratedMedia qrCode = new GeneratedMedia(
                GeneratedMediaKind.IMAGE,
                new byte[] {1, 2, 3},
                "chaoxing-login-qr.png",
                0L,
                "扫码登录");
        when(toolRegistry.execute(eq(LEARNING_QUERY), eq("{\"action\":\"login\"}"), any()))
                .thenAnswer(invocation -> {
                    ToolRunContext context = invocation.getArgument(2);
                    context.record(
                            LEARNING_QUERY,
                            new ToolExecution("请扫码", qrCode),
                            true);
                    return new ToolInvocation("请扫码", true, true);
                });

        AgentReply reply = runtime.reply(request("登录学习通")).orElseThrow();

        assertThat(reply.text()).contains("请扫码");
        assertThat(reply.generatedMedia()).containsExactly(qrCode);
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void routesAnyBoundedCourseReferenceToTheLearningTool() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(learningIntent());
        when(toolRegistry.available(LEARNING_QUERY)).thenReturn(true);
        when(toolRegistry.execute(
                eq(LEARNING_QUERY),
                eq("{\"action\":\"tasks\",\"courseReference\":\"C17\"}"),
                any()))
                .thenReturn(new ToolInvocation("真实任务列表", true, true));

        AgentReply reply = runtime.reply(request("查 C17 的任务")).orElseThrow();

        assertThat(reply.text()).contains("真实任务列表");
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void routesNaturalCourseNameToTaskQuery() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(learningIntent());
        when(toolRegistry.available(LEARNING_QUERY)).thenReturn(true);
        when(toolRegistry.execute(
                eq(LEARNING_QUERY),
                eq("{\"action\":\"tasks_by_name\",\"courseName\":\"烤串教程\"}"),
                any()))
                .thenReturn(new ToolInvocation("烤串教程的真实任务", true, true));

        AgentReply reply = runtime.reply(request("查询烤串教程课程任务")).orElseThrow();

        assertThat(reply.text()).contains("烤串教程的真实任务");
        verify(toolRegistry).execute(
                eq(LEARNING_QUERY),
                eq("{\"action\":\"tasks_by_name\",\"courseName\":\"烤串教程\"}"),
                any());
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void queriesLearningTasksBeforeExplainingTodoReadOnlyBoundary() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(learningIntent(TodoOperation.ADD));
        when(toolRegistry.available(LEARNING_QUERY)).thenReturn(true);
        when(toolRegistry.execute(
                eq(LEARNING_QUERY),
                eq("{\"action\":\"tasks_by_name\",\"courseName\":\"烤串教程\"}"),
                any()))
                .thenReturn(new ToolInvocation("烤串教程的真实任务", true, true));

        AgentReply reply = runtime.reply(
                request("查询烤串教程课程任务并自动添加至待办")).orElseThrow();

        assertThat(reply.text())
                .contains("烤串教程的真实任务")
                .contains("只读来源")
                .contains("不会");
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void refusesBatchCompletionOfLearningTasksWithoutCallingTheModel() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(learningIntent());

        AgentReply reply = runtime.reply(
                request("将所有学习通待办标记完成")).orElseThrow();

        assertThat(reply.text()).contains("只读数据").contains("不能").contains("学习通页面");
        verify(toolRegistry, never()).execute(eq(LEARNING_QUERY), anyString(), any());
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void refusesConcreteOcsRequestsWhenTheOcsToolIsUnavailable() throws Exception {
        AgentIntent ocsIntent = new AgentIntent(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                OcsStudyOperation.START,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                false,
                TodoOperation.NONE);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class))).thenReturn(ocsIntent);
        when(toolRegistry.available(START_OCS_STUDY)).thenReturn(false);
        when(toolRegistry.availabilityReason(START_OCS_STUDY)).thenReturn("disabled");

        AgentReply reply = runtime.reply(request("开始网课")).orElseThrow();

        assertThat(reply.text()).contains("OCS 学习当前不可用");
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void exposesOnlyOcsStartToolForExplicitOcsIntent() throws Exception {
        AgentIntent ocsIntent = new AgentIntent(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                OcsStudyOperation.START,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                false,
                TodoOperation.NONE);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class))).thenReturn(ocsIntent);
        when(toolRegistry.available(START_OCS_STUDY)).thenReturn(true);
        List<ToolDefinition> definitions = List.of(definition(START_OCS_STUDY, true));
        when(toolRegistry.listModelDefinitions(anyBoolean())).thenReturn(definitions);

        runtime.reply(request()).orElseThrow();

        verify(toolLoop).complete(
                anyList(),
                eq(definitions),
                eq(List.of(START_OCS_STUDY)),
                any(),
                anyString());
    }

    @Test
    void exposesOnlyOcsStatusToolForExplicitStatusIntent() throws Exception {
        AgentIntent ocsIntent = new AgentIntent(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                OcsStudyOperation.STATUS,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                false,
                TodoOperation.NONE);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class))).thenReturn(ocsIntent);
        when(toolRegistry.available(GET_OCS_STUDY_STATUS)).thenReturn(true);
        List<ToolDefinition> definitions = List.of(definition(GET_OCS_STUDY_STATUS, true));
        when(toolRegistry.listModelDefinitions(anyBoolean())).thenReturn(definitions);

        runtime.reply(request()).orElseThrow();

        verify(toolLoop).complete(
                anyList(),
                eq(definitions),
                eq(List.of(GET_OCS_STUDY_STATUS)),
                any(),
                anyString());
    }

    @Test
    void requiresSpreadsheetToolForExplicitWorkbookCreation() throws Exception {
        AgentIntent spreadsheetIntent = spreadsheetIntent(false);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(spreadsheetIntent);
        when(toolRegistry.available(GENERATE_SPREADSHEET)).thenReturn(true);
        List<ToolDefinition> definitions = List.of(
                definition(GENERATE_SPREADSHEET, true));
        when(toolRegistry.listModelDefinitions(anyBoolean())).thenReturn(definitions);

        runtime.reply(request()).orElseThrow();

        verify(toolLoop).complete(
                anyList(),
                eq(definitions),
                eq(List.of(GENERATE_SPREADSHEET)),
                any(),
                anyString());
    }

    @Test
    void plansSpreadsheetBeforeEmailDraft() throws Exception {
        AgentIntent spreadsheetEmailIntent = spreadsheetIntent(true);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(spreadsheetEmailIntent);
        when(toolRegistry.available(GENERATE_SPREADSHEET)).thenReturn(true);
        when(toolRegistry.available(SEND_EMAIL)).thenReturn(true);
        List<ToolDefinition> definitions = List.of(
                definition(GENERATE_SPREADSHEET, true),
                definition(SEND_EMAIL, true));
        when(toolRegistry.listModelDefinitions(anyBoolean())).thenReturn(definitions);

        runtime.reply(request()).orElseThrow();

        verify(toolLoop).complete(
                anyList(),
                eq(definitions),
                eq(List.of(GENERATE_SPREADSHEET, SEND_EMAIL)),
                any(),
                anyString());
    }

    @Test
    void plansFileSelectionBeforeEmailDraft() throws Exception {
        AgentIntent fileEmailIntent = fileSelectionEmailIntent();
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(fileEmailIntent);
        when(toolRegistry.available(SELECT_FILE)).thenReturn(true);
        when(toolRegistry.available(SEND_EMAIL)).thenReturn(true);
        List<ToolDefinition> definitions = List.of(
                definition(SELECT_FILE, true),
                definition(SEND_EMAIL, true));
        when(toolRegistry.listModelDefinitions(anyBoolean())).thenReturn(definitions);

        runtime.reply(request("把第一个文件发到邮箱")).orElseThrow();

        verify(toolLoop).complete(
                anyList(),
                eq(definitions),
                eq(List.of(SELECT_FILE, SEND_EMAIL)),
                any(),
                anyString());
        verify(toolRegistry, never()).execute(eq(SELECT_FILE), anyString(), any());
    }

    @Test
    void rejectsFileSelectionBeforeEmailWhenEmailIsUnavailable() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(fileSelectionEmailIntent());
        when(toolRegistry.available(SEND_EMAIL)).thenReturn(false);

        AgentReply reply = runtime.reply(request("把第一个文件发到邮箱")).orElseThrow();

        assertThat(reply.text()).contains("邮件未发送");
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
        verify(toolRegistry, never()).execute(eq(SELECT_FILE), anyString(), any());
    }

    @Test
    void givesAnActionableReplyWhenFileBrowseHasNoAllowedDirectory() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(capabilityIntent(false, true, false));
        when(toolRegistry.availabilityReason(BROWSE_FILES)).thenReturn("allowed_paths_missing");

        AgentReply reply = runtime.reply(request("搜索本地 PDF 文件")).orElseThrow();

        assertThat(reply.text())
                .contains("文件搜索当前不可用")
                .contains("BOT_FILE_BROWSE_ALLOWED_PATHS");
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void givesAnActionableReplyWhenPptxGenerationIsUnavailable() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(capabilityIntent(false, false, true));
        when(toolRegistry.availabilityReason(GENERATE_PRESENTATION))
                .thenReturn("office_presentation_unavailable");

        AgentReply reply = runtime.reply(request("生成 PPTX 演示文稿")).orElseThrow();

        assertThat(reply.text())
                .contains("PPTX 演示文稿当前不可用")
                .contains("BOT_PPTX_ENABLED=true");
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void routesConcreteFileSearchThroughTheModelOwnedToolLoop() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(capabilityIntent(false, true, false));
        when(toolRegistry.available(BROWSE_FILES)).thenReturn(true);
        when(toolRegistry.listModelDefinitions(anyBoolean()))
                .thenReturn(List.of(definition(BROWSE_FILES, true)));

        AgentReply reply = runtime.reply(request("正面.jpg")).orElseThrow();

        assertThat(reply.text()).isEqualTo("answer");
        verify(toolLoop).complete(
                anyList(),
                anyList(),
                eq(List.of(BROWSE_FILES)),
                any(),
                anyString());
        verify(toolRegistry, never()).execute(eq(BROWSE_FILES), anyString(), any());
    }

    @Test
    void routesNaturalDirectorySearchThroughTheModelOwnedToolLoop()
            throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(capabilityIntent(false, true, false));
        when(toolRegistry.available(BROWSE_FILES)).thenReturn(true);
        when(toolRegistry.listModelDefinitions(anyBoolean()))
                .thenReturn(List.of(definition(BROWSE_FILES, true)));

        AgentReply reply = runtime.reply(
                request("在D盘的campus中的self里面找文件")).orElseThrow();

        assertThat(reply.text()).isEqualTo("answer");
        verify(toolLoop).complete(
                anyList(),
                anyList(),
                eq(List.of(BROWSE_FILES)),
                any(),
                any());
        verify(toolRegistry, never()).execute(eq(BROWSE_FILES), anyString(), any());
    }

    @Test
    void givesAnActionableReplyWhenPlantUmlExecutableIsMissing() throws Exception {
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(diagramIntent());
        when(toolRegistry.availabilityReason(GENERATE_DIAGRAM)).thenReturn("executable_missing");

        AgentReply reply = runtime.reply(request("画一个流程图")).orElseThrow();

        assertThat(reply.text())
                .contains("PlantUML 图表当前不可用")
                .contains("PLANTUML_EXECUTABLE")
                .contains("plantuml.bat/plantuml");
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void requiresOnlyDocumentToolForExplicitPdfRequest() throws Exception {
        AgentIntent documentIntent = new AgentIntent(
                false, false, true, false,
                false, false, false, false, false,
                false, false, false, false,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                false,
                TodoOperation.NONE,
                false,
                SpreadsheetOperation.NONE,
                true);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(documentIntent);
        when(toolRegistry.available(GENERATE_DOCUMENT)).thenReturn(true);
        List<ToolDefinition> definitions = List.of(definition(GENERATE_DOCUMENT, true));
        when(toolRegistry.listModelDefinitions(anyBoolean())).thenReturn(definitions);

        runtime.reply(request()).orElseThrow();

        verify(toolLoop).complete(
                anyList(),
                eq(definitions),
                eq(List.of(GENERATE_DOCUMENT)),
                any(),
                anyString());
    }

    @Test
    void executesDailySummaryDirectlyWithoutModelToolSelection() throws Exception {
        AgentIntent summaryIntent = new AgentIntent(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                false,
                TodoOperation.SUMMARY);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(summaryIntent);
        when(toolRegistry.available(DAILY_SUMMARY)).thenReturn(true);
        when(toolRegistry.execute(eq(DAILY_SUMMARY), eq("{}"), any()))
                .thenReturn(new ToolInvocation("📋 今日要务\n当前没有待办。", true, true));

        AgentReply reply = runtime.reply(request()).orElseThrow();

        assertThat(reply.text()).contains("今日要务");
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    @Test
    void asksForSeparateMessagesWhenOneTurnContainsMultipleTodoCommands() throws Exception {
        AgentIntent multipleIntent = new AgentIntent(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                false,
                TodoOperation.MULTIPLE);
        when(intentResolver.resolve(anyList(), any(AgentRequest.class)))
                .thenReturn(multipleIntent);

        AgentReply reply = runtime.reply(request()).orElseThrow();

        assertThat(reply.text()).contains("多个待办操作", "分别发送");
        verify(toolLoop, never()).complete(anyList(), anyList(), anyList(), any(), anyString());
    }

    private static AgentRequest request() {
        return new AgentRequest(CONVERSATION_ID, "hello", List.of());
    }

    private static AgentRequest request(String prompt) {
        return new AgentRequest(CONVERSATION_ID, prompt, List.of());
    }

    private static AgentIntent neutralIntent() {
        return new AgentIntent(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                VoicePreference.DEFAULT);
    }

    private static AgentIntent learningIntent() {
        return learningIntent(TodoOperation.NONE);
    }

    private static AgentIntent learningIntent(TodoOperation todoOperation) {
        return new AgentIntent(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                OcsStudyOperation.NONE,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                false,
                todoOperation);
    }

    private static AgentIntent capabilityIntent(
            boolean diagramRequested,
            boolean fileBrowseRequested,
            boolean presentationRequested) {
        return new AgentIntent(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                diagramRequested,
                false,
                OcsStudyOperation.NONE,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                fileBrowseRequested,
                TodoOperation.NONE,
                false,
                SpreadsheetOperation.NONE,
                false,
                presentationRequested);
    }

    private static AgentIntent diagramIntent() {
        return capabilityIntent(true, false, false);
    }

    private static AgentIntent spreadsheetIntent(boolean emailRequested) {
        return new AgentIntent(
                false,
                false,
                false,
                true,
                emailRequested,
                emailRequested,
                false,
                false,
                false,
                false,
                false,
                false,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE);
    }

    private static AgentIntent fileSelectionEmailIntent() {
        return new AgentIntent(
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                OcsStudyOperation.NONE,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                false,
                TodoOperation.NONE,
                true,
                SpreadsheetOperation.NONE,
                false);
    }

    private static ToolDefinition definition(String name, boolean returnDirect) {
        return new ToolDefinition(name, "test", "{\"type\":\"object\"}", returnDirect);
    }
}
