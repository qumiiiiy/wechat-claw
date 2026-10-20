package com.weida.bot.wechatilinkbot.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.agent.intent.DesktopTaskIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.DesktopTaskOperation;
import com.weida.bot.wechatilinkbot.agent.intent.DiagramIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.EmailIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.FileBrowseIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.ImageIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.ImageReferenceDetector;
import com.weida.bot.wechatilinkbot.agent.intent.LearningIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.OcsStudyOperation;
import com.weida.bot.wechatilinkbot.agent.intent.QrCodeIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.SpeechIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.SpreadsheetIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.TextFileIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.TodoIntentDetector;
import com.weida.bot.wechatilinkbot.agent.intent.TodoOperation;
import com.weida.bot.wechatilinkbot.agent.intent.VoicePreferenceResolver;
import com.weida.bot.wechatilinkbot.agent.intent.WeatherIntentDetector;
import com.weida.bot.wechatilinkbot.capability.EmailCapability;
import com.weida.bot.wechatilinkbot.model.AgentRequest;
import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import com.weida.bot.wechatilinkbot.model.VoicePreference;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentIntentResolverTest {

    @Test
    void spreadsheetIntentWinsOverGenericFileIntent() {
        SpreadsheetIntentDetector spreadsheetDetector = mock(SpreadsheetIntentDetector.class);
        TextFileIntentDetector textFileDetector = mock(TextFileIntentDetector.class);
        VoicePreferenceResolver voiceResolver = mock(VoicePreferenceResolver.class);
        DesktopTaskIntentDetector desktopDetector = mock(DesktopTaskIntentDetector.class);
        FileBrowseIntentDetector fileBrowseDetector = mock(FileBrowseIntentDetector.class);
        when(spreadsheetDetector.hasIntent(any())).thenReturn(true);
        when(textFileDetector.hasIntent(any())).thenReturn(true);
        when(voiceResolver.resolve(any(), any())).thenReturn(VoicePreference.DEFAULT);
        when(desktopDetector.detect(any())).thenReturn(DesktopTaskOperation.NONE);
        when(fileBrowseDetector.hasIntent(any())).thenReturn(true);
        AgentIntentResolver resolver = new AgentIntentResolver(
                mock(SpeechIntentDetector.class),
                mock(ImageIntentDetector.class),
                textFileDetector,
                spreadsheetDetector,
                mock(EmailIntentDetector.class),
                mock(WeatherIntentDetector.class),
                mock(ImageReferenceDetector.class),
                voiceResolver,
                mock(QrCodeIntentDetector.class),
                mock(DiagramIntentDetector.class),
                desktopDetector,
                fileBrowseDetector,
                mock(TodoIntentDetector.class),
                learningDetectorWithoutOcs(),
                mock(EmailCapability.class));

        AgentIntent intent = resolver.resolve(
                List.of(),
                new AgentRequest(
                        "wechat-ilink|user-1",
                        "生成一份本周任务 Excel 文件",
                        List.of()));

        assertThat(intent.spreadsheetRequested()).isTrue();
        assertThat(intent.textFileRequested()).isFalse();
        assertThat(intent.fileBrowseRequested()).isFalse();
    }

    @Test
    void explicitLearningQueryWinsOverTodoAndFileBrowse() {
        VoicePreferenceResolver voiceResolver = mock(VoicePreferenceResolver.class);
        DesktopTaskIntentDetector desktopDetector = mock(DesktopTaskIntentDetector.class);
        LearningIntentDetector learningDetector = mock(LearningIntentDetector.class);
        TodoIntentDetector todoDetector = mock(TodoIntentDetector.class);
        FileBrowseIntentDetector fileBrowseDetector = mock(FileBrowseIntentDetector.class);
        when(voiceResolver.resolve(any(), any())).thenReturn(VoicePreference.DEFAULT);
        when(desktopDetector.detect(any())).thenReturn(DesktopTaskOperation.NONE);
        when(learningDetector.detectOcsOperation(anyList(), any())).thenReturn(OcsStudyOperation.NONE);
        when(learningDetector.hasLearningQuery(any())).thenReturn(true);
        when(todoDetector.detect(any())).thenReturn(TodoOperation.ADD);
        when(fileBrowseDetector.hasIntent(any())).thenReturn(true);
        EmailIntentDetector emailDetector = mock(EmailIntentDetector.class);
        EmailCapability emailCapability = mock(EmailCapability.class);
        when(emailDetector.hasIntent(anyList(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(true);
        when(emailCapability.hasPendingEmail(any())).thenReturn(true);
        AgentIntentResolver resolver = new AgentIntentResolver(
                mock(SpeechIntentDetector.class),
                mock(ImageIntentDetector.class),
                mock(TextFileIntentDetector.class),
                mock(SpreadsheetIntentDetector.class),
                emailDetector,
                mock(WeatherIntentDetector.class),
                mock(ImageReferenceDetector.class),
                voiceResolver,
                mock(QrCodeIntentDetector.class),
                mock(DiagramIntentDetector.class),
                desktopDetector,
                fileBrowseDetector,
                todoDetector,
                learningDetector,
                emailCapability);

        AgentIntent intent = resolver.resolve(
                List.of(),
                new AgentRequest(
                        "wechat-ilink|synthetic-user",
                        "查询学习通未完成任务",
                        List.of()));

        assertThat(intent.learningQueryRequested()).isTrue();
        assertThat(intent.todoOperation()).isEqualTo(TodoOperation.NONE);
        assertThat(intent.fileBrowseRequested()).isFalse();
        assertThat(intent.emailSendRequested()).isFalse();
    }

    @Test
    void shortContinuationUsesRecentLearningContext() {
        VoicePreferenceResolver voiceResolver = mock(VoicePreferenceResolver.class);
        DesktopTaskIntentDetector desktopDetector = mock(DesktopTaskIntentDetector.class);
        LearningIntentDetector learningDetector = mock(LearningIntentDetector.class);
        when(voiceResolver.resolve(any(), any())).thenReturn(VoicePreference.DEFAULT);
        when(desktopDetector.detect(any())).thenReturn(DesktopTaskOperation.NONE);
        when(learningDetector.detectOcsOperation(anyList(), any())).thenReturn(OcsStudyOperation.NONE);
        when(learningDetector.hasLearningQuery(any())).thenReturn(false);
        when(learningDetector.hasContinuationIntent(anyList(), any())).thenReturn(true);
        AgentIntentResolver resolver = new AgentIntentResolver(
                mock(SpeechIntentDetector.class),
                mock(ImageIntentDetector.class),
                mock(TextFileIntentDetector.class),
                mock(SpreadsheetIntentDetector.class),
                mock(EmailIntentDetector.class),
                mock(WeatherIntentDetector.class),
                mock(ImageReferenceDetector.class),
                voiceResolver,
                mock(QrCodeIntentDetector.class),
                mock(DiagramIntentDetector.class),
                desktopDetector,
                mock(FileBrowseIntentDetector.class),
                mock(TodoIntentDetector.class),
                learningDetector,
                mock(EmailCapability.class));

        AgentIntent intent = resolver.resolve(
                List.of(),
                new AgentRequest(
                        "wechat-ilink|synthetic-user",
                        "继续",
                        List.of()));

        assertThat(intent.learningQueryRequested()).isTrue();
    }

    @Test
    void explicitOcsStartIsSeparatedFromReadOnlyLearningQuery() {
        VoicePreferenceResolver voiceResolver = mock(VoicePreferenceResolver.class);
        DesktopTaskIntentDetector desktopDetector = mock(DesktopTaskIntentDetector.class);
        LearningIntentDetector learningDetector = mock(LearningIntentDetector.class);
        when(voiceResolver.resolve(any(), any())).thenReturn(VoicePreference.DEFAULT);
        when(desktopDetector.detect(any())).thenReturn(DesktopTaskOperation.NONE);
        when(learningDetector.detectOcsOperation(any())).thenReturn(OcsStudyOperation.START);
        when(learningDetector.hasLearningQuery(any())).thenReturn(true);
        AgentIntentResolver resolver = new AgentIntentResolver(
                mock(SpeechIntentDetector.class),
                mock(ImageIntentDetector.class),
                mock(TextFileIntentDetector.class),
                mock(SpreadsheetIntentDetector.class),
                mock(EmailIntentDetector.class),
                mock(WeatherIntentDetector.class),
                mock(ImageReferenceDetector.class),
                voiceResolver,
                mock(QrCodeIntentDetector.class),
                mock(DiagramIntentDetector.class),
                desktopDetector,
                mock(FileBrowseIntentDetector.class),
                mock(TodoIntentDetector.class),
                learningDetector,
                mock(EmailCapability.class));

        AgentIntent intent = resolver.resolve(
                List.of(),
                new AgentRequest(
                        "wechat-ilink|synthetic-user",
                        "用 OCS 帮我看合成课程",
                        List.of()));

        assertThat(intent.ocsStudyOperation()).isEqualTo(OcsStudyOperation.START);
        assertThat(intent.learningQueryRequested()).isFalse();
    }

    @Test
    void personalTodoWinsOverGenericFileBrowse() {
        VoicePreferenceResolver voiceResolver = mock(VoicePreferenceResolver.class);
        DesktopTaskIntentDetector desktopDetector = mock(DesktopTaskIntentDetector.class);
        TodoIntentDetector todoDetector = mock(TodoIntentDetector.class);
        FileBrowseIntentDetector fileBrowseDetector = mock(FileBrowseIntentDetector.class);
        LearningIntentDetector learningDetector = mock(LearningIntentDetector.class);
        when(voiceResolver.resolve(any(), any())).thenReturn(VoicePreference.DEFAULT);
        when(desktopDetector.detect(any())).thenReturn(DesktopTaskOperation.NONE);
        when(todoDetector.detect(any())).thenReturn(TodoOperation.ADD);
        when(fileBrowseDetector.hasIntent(any())).thenReturn(true);
        when(learningDetector.detectOcsOperation(anyList(), any())).thenReturn(OcsStudyOperation.NONE);
        when(learningDetector.hasLearningQuery(any())).thenReturn(false);
        when(learningDetector.hasContinuationIntent(anyList(), any())).thenReturn(true);
        AgentIntentResolver resolver = new AgentIntentResolver(
                mock(SpeechIntentDetector.class),
                mock(ImageIntentDetector.class),
                mock(TextFileIntentDetector.class),
                mock(SpreadsheetIntentDetector.class),
                mock(EmailIntentDetector.class),
                mock(WeatherIntentDetector.class),
                mock(ImageReferenceDetector.class),
                voiceResolver,
                mock(QrCodeIntentDetector.class),
                mock(DiagramIntentDetector.class),
                desktopDetector,
                fileBrowseDetector,
                todoDetector,
                learningDetector,
                mock(EmailCapability.class));

        AgentIntent intent = resolver.resolve(
                List.of(),
                new AgentRequest(
                        "wechat-ilink|synthetic-user",
                        "提醒我明天完成文件整理",
                        List.of()));

        assertThat(intent.todoOperation()).isEqualTo(TodoOperation.ADD);
        assertThat(intent.learningQueryRequested()).isFalse();
        assertThat(intent.fileBrowseRequested()).isFalse();
    }

    @Test
    void explicitImageRequestWinsOverStaleLearningContinuation() {
        VoicePreferenceResolver voiceResolver = mock(VoicePreferenceResolver.class);
        DesktopTaskIntentDetector desktopDetector = mock(DesktopTaskIntentDetector.class);
        LearningIntentDetector learningDetector = mock(LearningIntentDetector.class);
        ImageIntentDetector imageDetector = mock(ImageIntentDetector.class);
        when(voiceResolver.resolve(any(), any())).thenReturn(VoicePreference.DEFAULT);
        when(desktopDetector.detect(any())).thenReturn(DesktopTaskOperation.NONE);
        when(imageDetector.hasIntent(any())).thenReturn(true);
        when(learningDetector.detectOcsOperation(any())).thenReturn(OcsStudyOperation.NONE);
        when(learningDetector.hasLearningQuery(any())).thenReturn(false);
        when(learningDetector.hasContinuationIntent(anyList(), any())).thenReturn(true);
        AgentIntentResolver resolver = new AgentIntentResolver(
                mock(SpeechIntentDetector.class),
                imageDetector,
                mock(TextFileIntentDetector.class),
                mock(SpreadsheetIntentDetector.class),
                mock(EmailIntentDetector.class),
                mock(WeatherIntentDetector.class),
                mock(ImageReferenceDetector.class),
                voiceResolver,
                mock(QrCodeIntentDetector.class),
                mock(DiagramIntentDetector.class),
                desktopDetector,
                mock(FileBrowseIntentDetector.class),
                mock(TodoIntentDetector.class),
                learningDetector,
                mock(EmailCapability.class));

        AgentIntent intent = resolver.resolve(
                List.of(),
                new AgentRequest(
                        "wechat-ilink|synthetic-user",
                        "开始画一张海报",
                        List.of()));

        assertThat(intent.imageRequested()).isTrue();
        assertThat(intent.learningQueryRequested()).isFalse();
    }

    @Test
    void realDetectorsSwitchAwayFromLearningWhenCurrentTurnIsExplicit() {
        AgentIntentResolver resolver = realResolver();
        List<ConversationMessage> learningHistory = List.of(
                message("user", "查课程"),
                message("assistant", "课程列表：[C1] 合成课程"));

        AgentIntent courseTask = resolve(resolver, learningHistory, "C16任务");
        AgentIntent directTaskList = resolve(resolver, List.of(), "查看任务");
        AgentIntent pendingAndTodo = resolve(
                resolver,
                List.of(),
                "检查学习通所有课程未完成任务并自动添加至待办清单");
        AgentIntent namedCourseTask = resolve(resolver, List.of(), "查询烤串教程课程任务");
        AgentIntent learningCompletion = resolve(resolver, List.of(), "将所有学习通待办标记完成");
        AgentIntent todo = resolve(resolver, learningHistory, "提醒我明天交报告");
        AgentIntent image = resolve(resolver, learningHistory, "开始画一张海报");
        AgentIntent spreadsheet = resolve(resolver, learningHistory, "生成一份任务 Excel");
        AgentIntent standaloneFile = resolve(resolver, List.of(), "正面.jpg");
        AgentIntent directoryBrowse = resolve(resolver, List.of(), "在d盘的campus中的self里面");
        AgentIntent directoryContinuation = resolve(
                resolver,
                List.of(message("user", "把D盘下面的文件夹罗列一下")),
                "罗列一下");

        assertThat(courseTask.learningQueryRequested()).isTrue();
        assertThat(directTaskList.learningQueryRequested()).isTrue();
        assertThat(directTaskList.todoOperation()).isEqualTo(TodoOperation.NONE);
        assertThat(pendingAndTodo.learningQueryRequested()).isTrue();
        assertThat(pendingAndTodo.todoOperation()).isEqualTo(TodoOperation.NONE);
        assertThat(namedCourseTask.learningQueryRequested()).isTrue();
        assertThat(learningCompletion.learningQueryRequested()).isTrue();
        assertThat(todo.todoOperation()).isEqualTo(TodoOperation.ADD);
        assertThat(todo.learningQueryRequested()).isFalse();
        assertThat(image.imageRequested()).isTrue();
        assertThat(image.learningQueryRequested()).isFalse();
        assertThat(spreadsheet.spreadsheetRequested()).isTrue();
        assertThat(spreadsheet.learningQueryRequested()).isFalse();
        assertThat(standaloneFile.fileBrowseRequested()).isTrue();
        assertThat(directoryBrowse.fileBrowseRequested()).isTrue();
        assertThat(directoryContinuation.fileBrowseRequested()).isTrue();
    }

    @Test
    void realDetectorsDoNotResumeStaleLearningAfterAFeatureSwitch() {
        AgentIntentResolver resolver = realResolver();
        List<ConversationMessage> switchedHistory = List.of(
                message("user", "查课程"),
                message("assistant", "课程列表：[C1] 合成课程"),
                message("user", "提醒我明天交报告"),
                message("assistant", "待办已创建"));

        assertThat(resolve(resolver, switchedHistory, "继续").learningQueryRequested())
                .isFalse();
    }

    @Test
    void timeFilteredLearningTaskQueryRoutesToQueryTodos() {
        AgentIntentResolver resolver = realResolver();

        AgentIntent intent = resolve(
                resolver,
                List.of(),
                "过滤学习通中2026年7月前的未完成任务");

        assertThat(intent.todoOperation()).isEqualTo(TodoOperation.QUERY);
        assertThat(intent.learningQueryRequested()).isFalse();
    }

    @Test
    void desktopTaskWinsOverFileBrowse() {
        VoicePreferenceResolver voiceResolver = mock(VoicePreferenceResolver.class);
        DesktopTaskIntentDetector desktopDetector = mock(DesktopTaskIntentDetector.class);
        FileBrowseIntentDetector fileBrowseDetector = mock(FileBrowseIntentDetector.class);
        when(voiceResolver.resolve(any(), any())).thenReturn(VoicePreference.DEFAULT);
        when(desktopDetector.detect(any())).thenReturn(DesktopTaskOperation.REQUEST_OPEN_URL);
        when(fileBrowseDetector.hasIntent(any())).thenReturn(true);
        AgentIntentResolver resolver = new AgentIntentResolver(
                mock(SpeechIntentDetector.class),
                mock(ImageIntentDetector.class),
                mock(TextFileIntentDetector.class),
                mock(SpreadsheetIntentDetector.class),
                mock(EmailIntentDetector.class),
                mock(WeatherIntentDetector.class),
                mock(ImageReferenceDetector.class),
                voiceResolver,
                mock(QrCodeIntentDetector.class),
                mock(DiagramIntentDetector.class),
                desktopDetector,
                fileBrowseDetector,
                mock(TodoIntentDetector.class),
                mock(LearningIntentDetector.class),
                mock(EmailCapability.class));

        AgentIntent intent = resolver.resolve(
                List.of(),
                new AgentRequest(
                        "wechat-ilink|synthetic-user",
                        "在电脑上打开 https://example.edu.cn 文件页面",
                        List.of()));

        assertThat(intent.desktopTaskOperation())
                .isEqualTo(DesktopTaskOperation.REQUEST_OPEN_URL);
        assertThat(intent.fileBrowseRequested()).isFalse();
    }

    private static LearningIntentDetector learningDetectorWithoutOcs() {
        LearningIntentDetector detector = mock(LearningIntentDetector.class);
        when(detector.detectOcsOperation(anyList(), any())).thenReturn(OcsStudyOperation.NONE);
        return detector;
    }

    private static AgentIntentResolver realResolver() {
        return new AgentIntentResolver(
                new SpeechIntentDetector(),
                new ImageIntentDetector(),
                new TextFileIntentDetector(),
                new SpreadsheetIntentDetector(),
                new EmailIntentDetector(),
                new WeatherIntentDetector(),
                new ImageReferenceDetector(),
                new VoicePreferenceResolver(),
                new QrCodeIntentDetector(),
                new DiagramIntentDetector(),
                new DesktopTaskIntentDetector(),
                new FileBrowseIntentDetector(),
                new TodoIntentDetector(),
                new LearningIntentDetector(),
                mock(EmailCapability.class));
    }

    private static AgentIntent resolve(
            AgentIntentResolver resolver,
            List<ConversationMessage> history,
            String text) {
        return resolver.resolve(
                history,
                new AgentRequest("wechat-ilink|synthetic-user", text, List.of()));
    }

    private static ConversationMessage message(String role, String content) {
        Instant timestamp = Instant.parse("2026-08-04T00:00:00Z");
        return new ConversationMessage(
                null,
                "wechat-ilink|synthetic-user",
                role,
                content,
                timestamp,
                timestamp);
    }
}
