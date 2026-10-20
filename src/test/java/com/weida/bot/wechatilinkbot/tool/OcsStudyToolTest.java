package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.capability.OcsStudyCapability;
import com.weida.bot.wechatilinkbot.model.OcsStudySession;
import com.weida.bot.wechatilinkbot.tool.input.OcsStudySessionIdInput;
import com.weida.bot.wechatilinkbot.tool.input.StartOcsStudyInput;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OcsStudyToolTest {

    private static final String CONVERSATION_ID = "wechat-ilink|synthetic-owner";
    private final OcsStudyCapability capability = mock(OcsStudyCapability.class);
    private final ToolContext context =
            new ToolContext(CONVERSATION_ID, "用 OCS 帮我看合成课程");

    @Test
    void startsSessionWithTrustedConversationIdentity() {
        OcsStudySession created = session(
                "OS-synthetic", OcsStudySession.Status.CREATED, 0, "");
        when(capability.startSession(CONVERSATION_ID, "合成课程", "", ""))
                .thenReturn(created);
        when(capability.getStatus(CONVERSATION_ID, "OS-synthetic"))
                .thenReturn(created);
        StartOcsStudyTool tool = new StartOcsStudyTool(capability);

        ToolExecution execution = tool.execute(
                new StartOcsStudyInput("合成课程", ""),
                context);

        assertThat(tool.requiresExplicitIntent()).isTrue();
        assertThat(execution.content()).contains("OS-synthetic");
    }

    @Test
    void preservesCourseReferenceForExactBrowserSelection() {
        OcsStudySession created = session(
                "OS-referenced", OcsStudySession.Status.CREATED, 0, "");
        when(capability.startSession(CONVERSATION_ID, "合成课程", "C17", ""))
                .thenReturn(created);
        when(capability.getStatus(CONVERSATION_ID, "OS-referenced"))
                .thenReturn(created);
        StartOcsStudyTool tool = new StartOcsStudyTool(capability);

        ToolExecution execution = tool.execute(
                new StartOcsStudyInput("合成课程", "c17"),
                context);

        assertThat(execution.content()).contains("OS-referenced");
    }

    @Test
    void returnsOnlyOwnerVisibleStatusReportedByCapability() {
        when(capability.getStatus(CONVERSATION_ID, "OS-synthetic"))
                .thenReturn(session(
                        "OS-synthetic",
                        OcsStudySession.Status.RUNNING,
                        25,
                        "合成字幕"));
        GetOcsStudyStatusTool tool = new GetOcsStudyStatusTool(capability);

        ToolExecution execution = tool.execute(
                new OcsStudySessionIdInput("OS-synthetic"),
                context);

        assertThat(tool.requiresExplicitIntent()).isTrue();
        assertThat(execution.content())
                .contains("当前视频播放位置：25%")
                .contains("合成字幕");
    }

    @Test
    void explainsWhenBrowserHasNotClaimedTheSession() {
        when(capability.getStatus(CONVERSATION_ID, "OS-created"))
                .thenReturn(session(
                        "OS-created",
                        OcsStudySession.Status.CREATED,
                        0,
                        ""));
        GetOcsStudyStatusTool tool = new GetOcsStudyStatusTool(capability);

        ToolExecution execution = tool.execute(
                new OcsStudySessionIdInput("OS-created"),
                context);

        assertThat(execution.content())
                .contains("状态：CREATED")
                .contains("默认浏览器尚未领取任务")
                .contains("确认浏览器已打开课程页面");
    }

    @Test
    void doesNotReportRunningBeforeTargetCoursePageLoads() {
        when(capability.getStatus(CONVERSATION_ID, "OS-discovering"))
                .thenReturn(session(
                        "OS-discovering",
                        OcsStudySession.Status.DISCOVERING,
                        0,
                        ""));
        GetOcsStudyStatusTool tool = new GetOcsStudyStatusTool(capability);

        ToolExecution execution = tool.execute(
                new OcsStudySessionIdInput("OS-discovering"),
                context);

        assertThat(execution.content())
                .contains("状态：DISCOVERING")
                .contains("伴随脚本已连接")
                .doesNotContain("正在播放");
    }

    private static OcsStudySession session(
            String sessionId,
            OcsStudySession.Status status,
            int progress,
            String notes) {
        Instant now = Instant.parse("2026-07-31T00:00:00Z");
        return new OcsStudySession(
                sessionId,
                "合成课程",
                "",
                status,
                progress,
                "第一章",
                notes,
                "PROGRESS",
                null,
                now,
                now);
    }
}
