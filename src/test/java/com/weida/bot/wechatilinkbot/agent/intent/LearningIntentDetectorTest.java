package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningIntentDetectorTest {

    private final LearningIntentDetector detector = new LearningIntentDetector();

    @Test
    void detectCourseQuery() {
        assertThat(detector.hasLearningQuery("查看我的课程")).isTrue();
        assertThat(detector.hasLearningQuery("我的课程")).isTrue();
        assertThat(detector.hasLearningQuery("课程列表")).isTrue();
        assertThat(detector.hasLearningQuery("有什么课")).isTrue();
        assertThat(detector.hasLearningQuery("课表")).isTrue();
    }

    @Test
    void detectTaskQuery() {
        assertThat(detector.hasLearningQuery("学习通里有什么作业")).isTrue();
        assertThat(detector.hasLearningQuery("超星未完成的任务")).isTrue();
    }

    @Test
    void detectsDirectLearningTaskListRequestsWithoutStealingPersonalTodos() {
        assertThat(detector.hasLearningQuery("查看任务")).isTrue();
        assertThat(detector.hasLearningQuery("查所有未完成任务")).isTrue();
        assertThat(detector.hasLearningQuery("查看课程任务列表")).isTrue();
        assertThat(detector.hasLearningQuery("查第一门课的任务")).isTrue();
        assertThat(detector.hasLearningQuery("未完成的任务")).isFalse();
        assertThat(detector.hasLearningQuery("查看待办")).isFalse();
    }

    @Test
    void detectsCourseDetailAndNumberedFollowUp() {
        List<ConversationMessage> history = List.of(
                message("user", "查课程"),
                message("assistant", "📚 你的课程列表：[C1] 合成课程"));

        assertThat(detector.hasLearningQuery("查看课程详情")).isTrue();
        assertThat(detector.hasLearningQuery("C16任务")).isTrue();
        assertThat(detector.hasLearningQuery("查C2的详情")).isTrue();
        assertThat(detector.hasContinuationIntent(history, "第一门的任务"))
                .isTrue();
        assertThat(detector.hasContinuationIntent(List.of(), "第一门的任务"))
                .isFalse();
    }

    @Test
    void detectsOcsStudyRequest() {
        assertThat(detector.hasLearningQuery("用 OCS 帮我看这节网课")).isTrue();
        assertThat(detector.hasLearningQuery("帮我刷课并记录要点")).isTrue();
        assertThat(detector.detectOcsOperation("用 OCS 帮我看这节网课"))
                .isEqualTo(OcsStudyOperation.START);
        assertThat(detector.detectOcsOperation("OCS 学习会话 OS-test 的进度"))
                .isEqualTo(OcsStudyOperation.STATUS);
        assertThat(detector.detectOcsOperation("帮我重温课程视频"))
                .isEqualTo(OcsStudyOperation.START);
        assertThat(detector.detectOcsOperation("这门课程有哪些视频章节"))
                .isEqualTo(OcsStudyOperation.START);
        assertThat(detector.detectOcsOperation("查看这门课的视频任务"))
                .isEqualTo(OcsStudyOperation.START);
    }

    @Test
    void detectsNaturalCourseStartPhrase() {
        assertThat(detector.hasNaturalCourseStartIntent("第一门课开始学习")).isTrue();
        assertThat(detector.hasNaturalCourseStartIntent("开始看第二门课程")).isTrue();
        assertThat(detector.hasNaturalCourseStartIntent("查第一门课的任务")).isFalse();
    }

    @Test
    void detectsShortOcsContinuationOnlyAfterOcsContext() {
        List<ConversationMessage> ocsHistory = List.of(
                message("user", "用 OCS 看高等数学"),
                message("assistant", "OCS 学习会话 OS-synthetic 已创建"));
        List<ConversationMessage> ordinaryHistory = List.of(message(
                "assistant", "普通聊天回复"));

        assertThat(detector.detectOcsOperation(ocsHistory, "继续"))
                .isEqualTo(OcsStudyOperation.START);
        assertThat(detector.detectOcsOperation(ordinaryHistory, "继续"))
                .isEqualTo(OcsStudyOperation.NONE);
    }

    @Test
    void detectsReplayWithOrdinalAfterOcsContext() {
        List<ConversationMessage> history = List.of(
                message("user", "用 OCS 看合成课程"),
                message("assistant", "已创建 OCS 学习会话，请在默认浏览器继续。"));

        assertThat(detector.detectOcsOperation(history, "把第三个重温一下"))
                .isEqualTo(OcsStudyOperation.START);
    }

    @Test
    void staleLearningContextDoesNotCaptureContinuationAfterAnotherUserTask() {
        List<ConversationMessage> history = List.of(
                message("user", "查课程"),
                message("assistant", "📚 你的课程列表：[C1] 合成课程"),
                message("user", "生成一张海报"),
                message("assistant", "海报已生成"));

        assertThat(detector.hasContinuationIntent(history, "继续"))
                .isFalse();
    }

    @Test
    void detectsStatusAfterARealLengthOcsSessionId() {
        assertThat(detector.detectOcsOperation(
                "帮我查询 OCS 学习会话 OS-12345678-1234-1234-1234-123456789abc 的进度怎么样"))
                .isEqualTo(OcsStudyOperation.STATUS);
        assertThat(detector.detectOcsOperation(
                "OS-abcdef12-1234-5678-9abc-123456789abc 当前是什么情况"))
                .isEqualTo(OcsStudyOperation.STATUS);
    }

    @Test
    void genericProgressQuestionDoesNotBecomeOcsStatus() {
        assertThat(detector.detectOcsOperation("项目现在进度怎么样"))
                .isEqualTo(OcsStudyOperation.NONE);
        assertThat(detector.detectOcsOperation("开始查询课程列表"))
                .isEqualTo(OcsStudyOperation.NONE);
    }

    @Test
    void genericTodoDoesNotBecomeLearningQuery() {
        assertThat(detector.hasLearningQuery("未完成的任务")).isFalse();
        assertThat(detector.hasLearningQuery("新增待办：交报告")).isFalse();
    }

    @Test
    void noMatch() {
        assertThat(detector.hasLearningQuery("今天天气怎么样")).isFalse();
        assertThat(detector.hasLearningQuery("帮我生成一张图片")).isFalse();
    }

    private static ConversationMessage message(String role, String content) {
        return new ConversationMessage(
                null,
                "synthetic-conversation",
                role,
                content,
                Instant.parse("2026-08-04T00:00:00Z"),
                Instant.parse("2026-08-04T00:00:00Z"));
    }
}
