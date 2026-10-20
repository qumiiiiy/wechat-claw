package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingClient;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingLoginChallenge;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingLoginException;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingLoginService;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingLoginStatus;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingProperties;
import com.weida.bot.wechatilinkbot.infrastructure.learning.CourseJsonParser.CourseDetail;
import com.weida.bot.wechatilinkbot.model.CourseTask;
import com.weida.bot.wechatilinkbot.model.CourseTask.CompletionStatus;
import com.weida.bot.wechatilinkbot.model.CourseTask.TaskType;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LearningCapabilityTest {

    private final ChaoxingClient chaoxingClient = mock(ChaoxingClient.class);
    private final ChaoxingLoginService loginService = mock(ChaoxingLoginService.class);
    private final ChaoxingProperties properties = new ChaoxingProperties();
    private LearningCapability capability;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        capability = new LearningCapability(chaoxingClient, properties, loginService);
    }

    @Test
    void returnsQrCodeAsWechatReadyImage() {
        when(loginService.login()).thenReturn(new ChaoxingLoginChallenge(
                new byte[]{1, 2, 3}, Instant.parse("2026-08-04T12:03:00Z")));

        LearningLoginResult result = capability.login();

        assertThat(result.message()).contains("学习通 App").contains("查课程");
        assertThat(result.qrCode().kind()).isEqualTo(GeneratedMediaKind.IMAGE);
        assertThat(result.qrCode().fileName()).isEqualTo("chaoxing-login-qr.png");
    }

    @Test
    void reportsLoginFailureWithoutFakeImage() {
        when(loginService.login()).thenThrow(new ChaoxingLoginException("合成上游失败"));

        LearningLoginResult result = capability.login();

        assertThat(result.message()).isEqualTo("合成上游失败");
        assertThat(result.qrCode()).isNull();
    }

    @Test
    void asksForMobileConfirmationBeforeQueryingCourses() {
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.WAITING_CONFIRMATION);

        assertThat(capability.getCourseList()).contains("手机").contains("确认登录");
    }

    @Test
    void queriesCoursesAfterQrSessionIsAuthenticated() {
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(course(
                "synthetic-course", "synthetic-class", "合成课程")));

        assertThat(capability.getCourseList())
                .contains("[C1]")
                .contains("合成课程")
                .contains("合成教师")
                .contains("查 C1 的任务");
    }

    @Test
    void showsOpeningPeriodToDistinguishSameNamedCourses() {
        CourseDetail springCourse = datedCourse(
                "course-spring",
                "class-spring",
                "同名合成课程",
                "2026-03-03 07:47",
                "2026-06-26 07:47");
        CourseDetail autumnCourse = datedCourse(
                "course-autumn",
                "class-autumn",
                "同名合成课程",
                "2025-09-01 08:22",
                "2026-01-31 08:22");
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails())
                .thenReturn(List.of(springCourse, autumnCourse));

        assertThat(capability.getCourseList())
                .contains("[C1] 同名合成课程")
                .contains("开设：2026-03-03 至 2026-06-26")
                .contains("[C2] 同名合成课程")
                .contains("开设：2025-09-01 至 2026-01-31");
        assertThat(capability.getCourseDetails("C2"))
                .contains("[C2]【同名合成课程】")
                .contains("开设时间：2025-09-01 至 2026-01-31");
    }

    @Test
    void remembersStableIdentifiersBehindConversationLocalReference() {
        OcsCourseReferenceResolver referenceResolver = new OcsCourseReferenceResolver();
        LearningCapability rememberingCapability = new LearningCapability(
                chaoxingClient,
                properties,
                loginService,
                referenceResolver);
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(new CourseDetail(
                "4001",
                "3001",
                "5001",
                "合成课程",
                "合成班级",
                "合成教师",
                "合成学校",
                10,
                "",
                "",
                1,
                "",
                "")));

        assertThat(rememberingCapability.getCourseList("wechat-ilink|synthetic-owner"))
                .contains("[C1]");
        assertThat(rememberingCapability.getCourseDetails(
                "wechat-ilink|synthetic-owner",
                "C1"))
                .contains("[C1]【合成课程】");
        assertThat(referenceResolver.resolve(
                "wechat-ilink|synthetic-owner",
                "C1",
                "合成课程"))
                .hasParameter("courseid", "3001")
                .hasParameter("clazzid", "4001")
                .hasParameter("cpi", "5001");
        assertThat(referenceResolver.resolveLatestSelection(
                "wechat-ilink|synthetic-owner",
                "合成课程"))
                .get()
                .extracting(OcsCourseReferenceResolver.ResolvedCourse::courseReference)
                .isEqualTo("C1");
    }

    @Test
    void resolvesCourseReferenceAndReturnsTasks() {
        properties.setUid("different-login-uid");
        CourseDetail first = course("course-1", "class-1", "第一门课程", "enc-1");
        CourseDetail second = course("course-2", "class-2", "第二门课程", "enc-2");
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(first, second));
        when(chaoxingClient.fetchTasks("course-2", "class-2", "cpi-course-2", "enc-2"))
                .thenReturn(List.of(task("task-2", "第二门任务", CompletionStatus.PENDING, "course-2")));

        assertThat(capability.getTaskList("C2"))
                .contains("第二门课程")
                .contains("第二门任务")
                .contains("未完成");
    }

    @Test
    void usesCourseSpecificEncForSelectedTasks() {
        CourseDetail detail = courseWithEnc(
                "course-1", "class-1", "合成课程", "course-specific-enc");
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(detail));
        when(chaoxingClient.fetchTasks(
                "course-1", "class-1", "cpi-course-1", "course-specific-enc"))
                .thenReturn(List.of(task(
                        "task-1", "按课程参数查询的任务", CompletionStatus.PENDING, "course-1")));

        assertThat(capability.getTaskList("C1")).contains("按课程参数查询的任务");
        verify(chaoxingClient).fetchTasks(
                "course-1", "class-1", "cpi-course-1", "course-specific-enc");
    }

    @Test
    void resolvesDirectTaskIdentifiersAgainstCourseDetailsInsteadOfLoginUid() {
        properties.setUid("wrong-login-uid");
        CourseDetail detail = courseWithEnc(
                "course-1", "class-1", "合成课程", "course-specific-enc");
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(detail));
        when(chaoxingClient.fetchTasks(
                "course-1", "class-1", "cpi-course-1", "course-specific-enc"))
                .thenReturn(List.of(task(
                        "task-1", "按标识查询的任务", CompletionStatus.PENDING, "course-1")));

        assertThat(capability.getTaskList("course-1", "class-1", "model-enc"))
                .contains("按标识查询的任务");
        verify(chaoxingClient).fetchTasks(
                "course-1", "class-1", "cpi-course-1", "course-specific-enc");
    }

    @Test
    void rejectsTaskQueryWhenCourseSpecificCpiIsMissing() {
        CourseDetail detail = new CourseDetail(
                "class-1",
                "course-1",
                "",
                "合成课程",
                "合成班级",
                "合成教师",
                "合成学校",
                30,
                "",
                "",
                1,
                "",
                "");
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(detail));

        assertThat(capability.getTaskList("C1"))
                .contains("课程数据缺少任务查询标识")
                .contains("重新扫码登录");
    }

    @Test
    void aggregatesPendingTasksAcrossCoursesAndSkipsCompletedOnes() {
        CourseDetail first = course("course-1", "class-1", "第一门课程", "enc-1");
        CourseDetail second = course("course-2", "class-2", "第二门课程", "enc-2");
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(first, second));
        when(chaoxingClient.fetchTasks("course-1", "class-1", "cpi-course-1", "enc-1"))
                .thenReturn(List.of(
                        task("done", "已完成任务", CompletionStatus.COMPLETED, "course-1"),
                        task("pending", "待完成任务", CompletionStatus.PENDING, "course-1")));
        when(chaoxingClient.fetchTasks("course-2", "class-2", "cpi-course-2", "enc-2"))
                .thenReturn(List.of(task(
                        "unknown", "状态未知任务", CompletionStatus.UNKNOWN, "course-2")));

        assertThat(capability.checkAllPendingTasks())
                .contains("第一门课程", "待完成任务", "第二门课程", "状态未知任务")
                .doesNotContain("已完成任务");
    }

    @Test
    void continuesPendingSummaryWhenOneCourseFails() {
        CourseDetail first = course("course-1", "class-1", "失败课程");
        CourseDetail second = course("course-2", "class-2", "可用课程");
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(first, second));
        when(chaoxingClient.fetchTasks("course-1", "class-1", "cpi-course-1", ""))
                .thenThrow(new ChaoxingLoginException("单课查询失败"));
        when(chaoxingClient.fetchTasks("course-2", "class-2", "cpi-course-2", ""))
                .thenReturn(List.of(task(
                        "task-2", "保留下来的任务", CompletionStatus.PENDING, "course-2")));

        assertThat(capability.checkAllPendingTasks())
                .contains("保留下来的任务", "暂时无法查询", "结果可能不完整")
                .doesNotContain("失败课程】");
    }

    @Test
    void keepsUnknownStatusTasksInStructuredPendingSource() {
        CourseDetail detail = course("course-1", "class-1", "合成课程");
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(detail));
        when(chaoxingClient.fetchTasks("course-1", "class-1", "cpi-course-1", ""))
                .thenReturn(List.of(
                        task("done", "已完成", CompletionStatus.COMPLETED, "course-1"),
                        task("unknown", "状态未知", CompletionStatus.UNKNOWN, "course-1")));

        assertThat(capability.fetchAllPendingCourseTasks())
                .extracting(LearningCapability.EnrichedCourseTask::task)
                .extracting(CourseTask::taskName)
                .containsExactly("状态未知");
    }

    @Test
    void reportsAuthenticationFailureToPendingSourceInsteadOfReturningEmpty() {
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.NOT_STARTED);

        assertThatThrownBy(capability::fetchAllPendingCourseTasks)
                .isInstanceOf(PendingItemSourceException.class)
                .hasMessageContaining("尚未登录学习通");
    }

    @Test
    void formatsTaskDeadlineInShanghaiTime() {
        CourseDetail detail = course("course-1", "class-1", "合成课程");
        CourseTask task = taskWithDeadline(
                "task-1",
                "有截止时间的任务",
                CompletionStatus.PENDING,
                "course-1",
                Instant.parse("2026-08-15T15:59:59Z"));
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(detail));
        when(chaoxingClient.fetchTasks("course-1", "class-1", "cpi-course-1", ""))
                .thenReturn(List.of(task));

        assertThat(capability.getTaskList("C1"))
                .contains("截止：2026-08-15 23:59:59")
                .doesNotContain("2026-08-15T15:59:59Z");
    }

    @Test
    void rejectsUnknownCourseReference() {
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(
                course("course-1", "class-1", "第一门课程")));

        assertThat(capability.getTaskList("C9")).contains("课程引用不存在");
    }

    @Test
    void boundsTaskOutput() {
        CourseDetail detail = course("course-1", "class-1", "合成课程");
        List<CourseTask> tasks = new ArrayList<>();
        for (int index = 1; index <= 101; index++) {
            tasks.add(task(
                    "task-" + index,
                    "任务" + index,
                    CompletionStatus.PENDING,
                    "course-1"));
        }
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(detail));
        when(chaoxingClient.fetchTasks("course-1", "class-1", "cpi-course-1", ""))
                .thenReturn(tasks);

        assertThat(capability.getTaskList("C1"))
                .contains("任务100", "仅显示前 100 个任务")
                .doesNotContain("任务101");
    }

    @Test
    void listsAllCoursesWithoutCourseCountBoundary() {
        List<CourseDetail> details = new ArrayList<>();
        for (int index = 1; index <= 51; index++) {
            details.add(course("course-" + index, "class-" + index, "课程" + index));
        }
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(details);

        assertThat(capability.getCourseList())
                .contains("[C51] 课程51")
                .doesNotContain("仅显示前");
    }

    @Test
    void checksAllCoursesInPendingSummaryWithoutCourseCountBoundary() {
        List<CourseDetail> details = new ArrayList<>();
        for (int index = 1; index <= 21; index++) {
            details.add(course("course-" + index, "class-" + index, "课程" + index));
            when(chaoxingClient.fetchTasks(
                    "course-" + index,
                    "class-" + index,
                    "cpi-course-" + index,
                    ""))
                    .thenReturn(List.of());
        }
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(details);

        assertThat(capability.checkAllPendingTasks())
                .isEqualTo("没有发现未完成任务。")
                .doesNotContain("仅检查前");
    }

    @Test
    void passesEncFromCourseDetailToTaskQuery() {
        CourseDetail detail = course("course-1", "class-1", "合成课程", "test-enc-value");
        when(chaoxingClient.refreshLoginStatus())
                .thenReturn(ChaoxingLoginStatus.AUTHENTICATED);
        when(chaoxingClient.fetchCourseDetails()).thenReturn(List.of(detail));
        when(chaoxingClient.fetchTasks("course-1", "class-1", "cpi-course-1", "test-enc-value"))
                .thenReturn(List.of(task("task-1", "测试任务", CompletionStatus.PENDING, "course-1")));

        assertThat(capability.getTaskList("C1"))
                .contains("测试任务");
    }

    private static CourseDetail course(String courseId, String classId, String name) {
        return course(courseId, classId, name, "");
    }

    private static CourseDetail course(String courseId, String classId, String name, String enc) {
        return new CourseDetail(
                classId,
                courseId,
                "cpi-" + courseId,
                name,
                "合成班级",
                "合成教师",
                "合成学校",
                30,
                "",
                "",
                1,
                "",
                enc);
    }

    private static CourseDetail courseWithEnc(
            String courseId,
            String classId,
            String name,
            String enc) {
        return new CourseDetail(
                classId,
                courseId,
                "cpi-" + courseId,
                name,
                "合成班级",
                "合成教师",
                "合成学校",
                30,
                "",
                "",
                1,
                "",
                enc);
    }

    private static CourseDetail datedCourse(
            String courseId,
            String classId,
            String name,
            String beginDate,
            String endDate) {
        return new CourseDetail(
                classId,
                courseId,
                "cpi-" + courseId,
                name,
                "合成班级",
                "合成教师",
                "合成学校",
                30,
                beginDate,
                endDate,
                1,
                "",
                "");
    }

    private static CourseTask task(
            String taskId,
            String name,
            CompletionStatus status,
            String courseId) {
        return new CourseTask(
                taskId,
                name,
                TaskType.HOMEWORK,
                status,
                null,
                courseId,
                "synthetic",
                "");
    }

    private static CourseTask taskWithDeadline(
            String taskId,
            String name,
            CompletionStatus status,
            String courseId,
            Instant deadline) {
        return new CourseTask(
                taskId,
                name,
                TaskType.HOMEWORK,
                status,
                deadline,
                courseId,
                "synthetic",
                "");
    }
}
