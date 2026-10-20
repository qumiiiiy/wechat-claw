package com.weida.bot.wechatilinkbot.agent;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GET_DESKTOP_TASK_STATUS;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.SELECT_FILE;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.SEND_EMAIL;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.START_OCS_STUDY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.agent.intent.DesktopTaskOperation;
import com.weida.bot.wechatilinkbot.agent.intent.OcsStudyOperation;
import com.weida.bot.wechatilinkbot.agent.intent.SpreadsheetOperation;
import com.weida.bot.wechatilinkbot.agent.intent.TodoOperation;
import com.weida.bot.wechatilinkbot.capability.EmotionalProfileService;
import com.weida.bot.wechatilinkbot.model.AgentReply;
import com.weida.bot.wechatilinkbot.model.AgentRequest;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.VoicePreference;
import com.weida.bot.wechatilinkbot.persistence.ConversationMemory;
import com.weida.bot.wechatilinkbot.persistence.ConversationMemoryProperties;
import com.weida.bot.wechatilinkbot.persistence.GeneratedFileMemory;
import com.weida.bot.wechatilinkbot.persistence.GeneratedFileMemoryProperties;
import com.weida.bot.wechatilinkbot.persistence.GeneratedImageMemory;
import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import com.weida.bot.wechatilinkbot.tool.ToolExecution;
import com.weida.bot.wechatilinkbot.tool.ToolInvocation;
import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import com.weida.bot.wechatilinkbot.tool.ToolRunContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class AgentRuntimeNaturalReferenceTest {

    private static final String CONVERSATION_ID = "wechat|natural-reference";

    @Test
    void sendsAFileByNaturalOrdinalWithoutCallingTheModel() throws Exception {
        ConversationReferenceStore references = references();
        String fileId = "file-12345678-1234-1234-1234-123456789abc";
        references.rememberGroup(
                CONVERSATION_ID,
                ConversationReferenceStore.ReferenceType.FILE,
                List.of(new ConversationReferenceStore.ReferenceDraft(
                        fileId,
                        "课程表.pdf",
                        java.util.Map.of())));
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.available(SELECT_FILE)).thenReturn(true);
        when(registry.listModelDefinitions(anyBoolean())).thenReturn(List.of());
        when(registry.execute(eq(SELECT_FILE), anyString(), any()))
                .thenReturn(new ToolInvocation(
                        "{\"status\":\"ok\",\"file_name\":\"课程表.pdf\"}",
                        true,
                        true));
        AgentRuntime runtime = runtime(
                fileIntent(false),
                references,
                registry);

        AgentReply reply = runtime.reply(new AgentRequest(
                CONVERSATION_ID,
                "把第一个文件发给我",
                List.of())).orElseThrow();

        assertThat(reply.text()).contains("课程表.pdf").doesNotContain(fileId);
        verify(registry).execute(eq(SELECT_FILE), eq("{\"fileId\":\"" + fileId + "\"}"), any());
    }

    @Test
    void resolvesDesktopProgressToTheMostRecentSessionObject() throws Exception {
        ConversationReferenceStore references = references();
        String taskId = "DT-12345678";
        references.rememberGroup(
                CONVERSATION_ID,
                ConversationReferenceStore.ReferenceType.DESKTOP_TASK,
                List.of(new ConversationReferenceStore.ReferenceDraft(
                        taskId,
                        "打开课程页面",
                        java.util.Map.of())));
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.available(GET_DESKTOP_TASK_STATUS)).thenReturn(true);
        when(registry.listModelDefinitions(anyBoolean())).thenReturn(List.of());
        when(registry.execute(eq(GET_DESKTOP_TASK_STATUS), anyString(), any()))
                .thenReturn(new ToolInvocation(
                        "任务 " + taskId + " 当前状态：RUNNING。",
                        true,
                        true));
        AgentRuntime runtime = runtime(
                desktopStatusIntent(),
                references,
                registry);

        AgentReply reply = runtime.reply(new AgentRequest(
                CONVERSATION_ID,
                "进度怎么样",
                List.of())).orElseThrow();

        assertThat(reply.text()).contains("进行中").doesNotContain(taskId);
        verify(registry).execute(
                eq(GET_DESKTOP_TASK_STATUS),
                eq("{\"taskId\":\"" + taskId + "\"}"),
                any());
    }

    @Test
    void startsOcsWithTheNaturalCourseReferenceWithoutCallingTheModel() throws Exception {
        ConversationReferenceStore references = references();
        references.rememberGroup(
                CONVERSATION_ID,
                ConversationReferenceStore.ReferenceType.COURSE,
                List.of(new ConversationReferenceStore.ReferenceDraft(
                        "C1",
                        "高等数学",
                        java.util.Map.of())));
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.available(START_OCS_STUDY)).thenReturn(true);
        when(registry.listModelDefinitions(anyBoolean())).thenReturn(List.of());
        when(registry.execute(eq(START_OCS_STUDY), anyString(), any()))
                .thenReturn(new ToolInvocation(
                        "OCS 学习会话 OS-12345678 已创建，状态：CREATED。",
                        true,
                        true));
        AgentRuntime runtime = runtime(
                courseStartIntent(),
                references,
                registry);

        AgentReply reply = runtime.reply(new AgentRequest(
                CONVERSATION_ID,
                "第一门课开始学习",
                List.of())).orElseThrow();

        assertThat(reply.text()).contains("当前学习", "已创建").doesNotContain("OS-12345678");
        verify(registry).execute(
                eq(START_OCS_STUDY),
                eq("{\"courseName\":\"高等数学\",\"courseReference\":\"C1\"}"),
                any());
    }

    @Test
    void attachesNaturalFileToEmailWithoutDeliveringItToWechat() throws Exception {
        ConversationReferenceStore references = references();
        String fileId = "file-12345678-1234-1234-1234-123456789abc";
        references.rememberGroup(
                CONVERSATION_ID,
                ConversationReferenceStore.ReferenceType.FILE,
                List.of(new ConversationReferenceStore.ReferenceDraft(
                        fileId,
                        "课程表.pdf",
                        java.util.Map.of())));
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.available(SELECT_FILE)).thenReturn(true);
        when(registry.available("send_email")).thenReturn(true);
        when(registry.listModelDefinitions(anyBoolean()))
                .thenReturn(List.of(new ToolDefinition(
                        "send_email", "email", "{}", true)));
        GeneratedMedia media = new GeneratedMedia(
                GeneratedMediaKind.FILE,
                new byte[] {1, 2, 3},
                "课程表.pdf",
                0L,
                "");
        doAnswer(invocation -> {
            ToolRunContext context = invocation.getArgument(2);
            context.record(
                    SELECT_FILE,
                    new ToolExecution("{\"status\":\"ok\",\"file_name\":\"课程表.pdf\"}", media));
            return new ToolInvocation("{\"status\":\"ok\"}", true, true);
        }).when(registry).execute(eq(SELECT_FILE), anyString(), any());
        RuntimeFixture fixture = runtimeFixture(fileIntent(true), references, registry);

        AgentReply reply = fixture.runtime().reply(new AgentRequest(
                CONVERSATION_ID,
                "把第一个文件发到邮箱",
                List.of())).orElseThrow();

        ArgumentCaptor<ToolRunContext> contextCaptor = ArgumentCaptor.forClass(ToolRunContext.class);
        verify(fixture.toolLoop()).complete(
                anyList(),
                anyList(),
                eq(List.of(SELECT_FILE, SEND_EMAIL)),
                contextCaptor.capture(),
                anyString());
        assertThat(reply.generatedMedia()).isEmpty();
        assertThat(contextCaptor.getValue().generatedMedia()).isEmpty();
        assertThat(contextCaptor.getValue().trustedContext().availableGeneratedFiles())
                .containsExactly(media);
        verify(registry).execute(
                eq(SELECT_FILE),
                eq("{\"fileId\":\"" + fileId + "\"}"),
                any());
    }

    private static ConversationReferenceStore references() {
        return new ConversationReferenceStore(
                Duration.ofMinutes(30),
                20,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));
    }

    private static AgentIntent fileIntent(boolean email) {
        return new AgentIntent(
                false, false, false, false,
                email, email, false, false, false,
                false, false, false, false,
                OcsStudyOperation.NONE,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                false,
                TodoOperation.NONE,
                true,
                SpreadsheetOperation.NONE,
                false);
    }

    private static AgentIntent desktopStatusIntent() {
        return new AgentIntent(
                false, false, false, false,
                false, false, false, false, false,
                false, false, false, false,
                OcsStudyOperation.NONE,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.STATUS,
                false,
                TodoOperation.NONE,
                false,
                SpreadsheetOperation.NONE,
                false);
    }

    private static AgentIntent courseStartIntent() {
        return new AgentIntent(
                false, false, false, false,
                false, false, false, false, false,
                false, false, false, false,
                OcsStudyOperation.START,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE,
                false,
                TodoOperation.NONE,
                false,
                SpreadsheetOperation.NONE,
                false);
    }

    private static AgentRuntime runtime(
            AgentIntent intent,
            ConversationReferenceStore references,
            ToolRegistry registry) throws java.io.IOException {
        return runtimeFixture(intent, references, registry).runtime();
    }

    private static RuntimeFixture runtimeFixture(
            AgentIntent intent,
            ConversationReferenceStore references,
            ToolRegistry registry) throws java.io.IOException {
        AgentProperties properties = new AgentProperties();
        AgentModelClient modelClient = mock(AgentModelClient.class);
        when(modelClient.available()).thenReturn(true);
        ConversationMemory memory = mock(ConversationMemory.class);
        when(memory.listRecent(anyString(), any(Integer.class))).thenReturn(List.of());
        GeneratedImageMemory imageMemory = mock(GeneratedImageMemory.class);
        EmotionalProfileService emotional = mock(EmotionalProfileService.class);
        when(emotional.extractAndUpdateProfile(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        AgentIntentResolver resolver = mock(AgentIntentResolver.class);
        when(resolver.resolve(anyList(), any(AgentRequest.class))).thenReturn(intent);
        AgentPromptAssembler promptAssembler = mock(AgentPromptAssembler.class);
        when(promptAssembler.historyPrompt(any(AgentRequest.class))).thenReturn("prompt");
        AgentToolLoop toolLoop = mock(AgentToolLoop.class);
        when(toolLoop.complete(anyList(), anyList(), anyList(), any(), anyString()))
                .thenReturn("model");
        AgentResponsePolicy responsePolicy = mock(AgentResponsePolicy.class);
        when(responsePolicy.finish(anyString(), any(), any(), anyString()))
                .thenReturn(new AgentReply("model", List.of()));
        return new RuntimeFixture(
                new AgentRuntime(
                        properties,
                        modelClient,
                        new ConversationCoordinator(properties),
                        memory,
                        imageMemory,
                        new GeneratedFileMemory(new GeneratedFileMemoryProperties()),
                        emotional,
                        resolver,
                        promptAssembler,
                        toolLoop,
                        responsePolicy,
                        registry,
                        new ObjectMapper(),
                        new ConversationContextCompactor(
                                properties,
                                modelClient,
                                memory,
                                new ConversationMemoryProperties()),
                        references),
                toolLoop);
    }

    private record RuntimeFixture(AgentRuntime runtime, AgentToolLoop toolLoop) {
    }

}
