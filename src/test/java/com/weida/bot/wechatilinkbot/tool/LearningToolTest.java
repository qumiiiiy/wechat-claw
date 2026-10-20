package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.capability.LearningCapability;
import com.weida.bot.wechatilinkbot.capability.LearningLoginResult;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.tool.input.LearningToolInput;
import org.junit.jupiter.api.Test;

class LearningToolTest {

    private final LearningCapability capability = mock(LearningCapability.class);
    private final LearningTool tool = new LearningTool(capability);
    private final ToolContext context =
            new ToolContext("wechat-ilink|synthetic-user", "查询学习通课程");

    @Test
    void delegatesCourseQueryToCapability() {
        when(capability.getCourseList("wechat-ilink|synthetic-user"))
                .thenReturn("合成课程列表");

        ToolExecution execution = tool.execute(
                new LearningToolInput("courses", "", "", "", "", ""),
                context);

        assertThat(execution.content()).isEqualTo("合成课程列表");
    }

    @Test
    void rejectsTaskQueryWithoutRequiredIdentifiers() {
        ToolExecution execution = tool.execute(
                new LearningToolInput("tasks", "", "", "", "", ""),
                context);

        assertThat(execution.content()).contains("课程名称").contains("courseId/classId");
    }

    @Test
    void returnsQrImageForLogin() {
        GeneratedMedia qrCode = new GeneratedMedia(
                GeneratedMediaKind.IMAGE,
                new byte[]{1, 2, 3},
                "chaoxing-login-qr.png",
                0L,
                "扫码登录");
        when(capability.login()).thenReturn(new LearningLoginResult("请扫码", qrCode));

        ToolExecution execution = tool.execute(
                new LearningToolInput("login", "", "", "", "", ""),
                context);

        assertThat(tool.producesMedia()).isTrue();
        assertThat(tool.returnDirect()).isTrue();
        assertThat(execution.content()).isEqualTo("请扫码");
        assertThat(execution.generatedMedia()).isEqualTo(qrCode);
    }

    @Test
    void resolvesTasksByCourseReference() {
        when(capability.getTaskList("C2")).thenReturn("合成任务列表");

        ToolExecution execution = tool.execute(
                new LearningToolInput("tasks", "", "", "", "", "C2"),
                context);

        assertThat(execution.content()).isEqualTo("合成任务列表");
    }

    @Test
    void resolvesTasksByCourseNameCompatibilityAction() {
        when(capability.getTaskList("数据结构")).thenReturn("按名称查询的合成任务列表");

        ToolExecution execution = tool.execute(
                new LearningToolInput("tasks_by_name", "", "", "", "数据结构", ""),
                context);

        assertThat(execution.content()).isEqualTo("按名称查询的合成任务列表");
    }

    @Test
    void resolvesTasksByCourseIdentifiersCompatibilityAction() {
        when(capability.getTaskList("course-1", "class-1", "enc-1"))
                .thenReturn("按课程标识查询的合成任务列表");

        ToolExecution execution = tool.execute(
                new LearningToolInput("tasks", "course-1", "class-1", "enc-1", "", ""),
                context);

        assertThat(execution.content()).isEqualTo("按课程标识查询的合成任务列表");
    }

    @Test
    void rejectsTasksByNameWithoutCourseName() {
        ToolExecution execution = tool.execute(
                new LearningToolInput("tasks_by_name", "", "", "", "", ""),
                context);

        assertThat(execution.content()).contains("courseName");
    }
}
