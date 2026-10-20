package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.capability.LearningCapability;
import com.weida.bot.wechatilinkbot.capability.OcsCourseReferenceResolver;
import com.weida.bot.wechatilinkbot.capability.OcsStudyCapability;
import com.weida.bot.wechatilinkbot.capability.OcsStudyProperties;
import com.weida.bot.wechatilinkbot.infrastructure.desktop.WindowsDesktopTaskExecutor;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingClient;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingLoginService;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingLoginStatus;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingProperties;
import com.weida.bot.wechatilinkbot.infrastructure.learning.CourseJsonParser.CourseDetail;
import com.weida.bot.wechatilinkbot.tool.input.LearningToolInput;
import com.weida.bot.wechatilinkbot.tool.input.StartOcsStudyInput;
import java.net.URI;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OcsCourseSelectionFlowTest {

    private static final String OWNER = "wechat-ilink|synthetic-owner";
    private static final String DUPLICATE_NAME = "同名合成课程";

    @Test
    void reusesSelectedC17WhenStartToolReceivesOnlyCourseName() {
        ChaoxingClient chaoxingClient = mock(ChaoxingClient.class);
        ChaoxingProperties chaoxingProperties = new ChaoxingProperties();
        chaoxingProperties.setEnabled(true);
        ChaoxingLoginService loginService = mock(ChaoxingLoginService.class);
        OcsCourseReferenceResolver referenceResolver = new OcsCourseReferenceResolver();
        LearningCapability learningCapability = new LearningCapability(
                chaoxingClient,
                chaoxingProperties,
                loginService,
                referenceResolver);
        when(chaoxingClient.refreshLoginStatus()).thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(courses());
        LearningTool learningTool = new LearningTool(learningCapability);
        ToolContext context = new ToolContext(OWNER, "查看 C17 课程详情");

        ToolExecution details = learningTool.execute(
                new LearningToolInput("details", "", "", "", "", "C17"),
                context);

        assertThat(details.content()).contains("[C17]【" + DUPLICATE_NAME + "】");

        OcsStudyProperties ocsProperties = new OcsStudyProperties();
        ocsProperties.setEnabled(true);
        WindowsDesktopTaskExecutor desktopTaskExecutor = mock(WindowsDesktopTaskExecutor.class);
        when(desktopTaskExecutor.available()).thenReturn(true);
        OcsStudyCapability ocsCapability = new OcsStudyCapability(
                ocsProperties,
                desktopTaskExecutor,
                referenceResolver);
        StartOcsStudyTool startTool = new StartOcsStudyTool(ocsCapability);

        startTool.execute(new StartOcsStudyInput(DUPLICATE_NAME, ""), context);

        ArgumentCaptor<URI> openedUrl = ArgumentCaptor.forClass(URI.class);
        verify(desktopTaskExecutor).openUrl(openedUrl.capture());
        assertThat(openedUrl.getValue())
                .hasParameter("courseid", "3017")
                .hasParameter("clazzid", "4017")
                .hasParameter("cpi", "5017");
    }

    private static List<CourseDetail> courses() {
        return IntStream.rangeClosed(1, 17)
                .mapToObj(index -> new CourseDetail(
                        "40" + String.format("%02d", index),
                        "30" + String.format("%02d", index),
                        "50" + String.format("%02d", index),
                        index == 9 || index == 17 ? DUPLICATE_NAME : "合成课程-" + index,
                        "合成班级-" + index,
                        "合成教师",
                        "合成学校",
                        10,
                        "",
                        "",
                        1,
                        "",
                        ""))
                .toList();
    }
}
