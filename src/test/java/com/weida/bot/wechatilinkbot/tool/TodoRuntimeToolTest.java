package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.PendingItemReference;
import com.weida.bot.wechatilinkbot.capability.PendingItemService;
import com.weida.bot.wechatilinkbot.capability.CourseTaskPendingAdapter;
import com.weida.bot.wechatilinkbot.capability.LearningCapability;
import com.weida.bot.wechatilinkbot.capability.PersonalTodoPendingAdapter;
import com.weida.bot.wechatilinkbot.capability.ReminderState;
import com.weida.bot.wechatilinkbot.capability.StudentTodoCapability;
import com.weida.bot.wechatilinkbot.model.CourseTask;
import com.weida.bot.wechatilinkbot.model.CourseTask.CompletionStatus;
import com.weida.bot.wechatilinkbot.model.CourseTask.TaskType;
import com.weida.bot.wechatilinkbot.model.TodoFilter;
import com.weida.bot.wechatilinkbot.persistence.InMemoryStudentTodoMemory;
import com.weida.bot.wechatilinkbot.tool.input.NoArgumentsToolInput;
import com.weida.bot.wechatilinkbot.tool.input.TodoQueryInput;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TodoRuntimeToolTest {

    private static final String CONVERSATION = "wechat-ilink|demo-user";

    private StudentTodoCapability capability;
    private PendingItemService pendingItemService;
    private PendingItemReference reference;
    private ReminderState reminderState;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        capability = new StudentTodoCapability(
                new InMemoryStudentTodoMemory(),
                Clock.fixed(
                        Instant.parse("2026-08-01T02:00:00Z"),
                        StudentTodoCapability.SHANGHAI));
        pendingItemService = new PendingItemService(
                List.of(new PersonalTodoPendingAdapter(capability)),
                Clock.fixed(
                        Instant.parse("2026-08-01T02:00:00Z"),
                        StudentTodoCapability.SHANGHAI));
        reference = new PendingItemReference();
        reminderState = new ReminderState();
        context = new ToolContext(CONVERSATION, "演示待办");
    }

    @Test
    void noArgumentToolsExposeEmptySchemasAndExecuteWithEmptyJson() {
        capability.createTodo(CONVERSATION, "演示任务", "今天18:00");
        List<AgentTool<?>> tools = List.of(
                new ListTodosTool(pendingItemService, reminderState, reference),
                new TodayTodosTool(pendingItemService, reminderState, reference),
                new DailySummaryTool(capability));
        ToolRegistry registry = new ToolRegistry(tools, new ObjectMapper());

        assertThat(registry.listDefinitions(true, Set.of(
                ToolNames.LIST_TODOS,
                ToolNames.TODAY_TODOS,
                ToolNames.DAILY_SUMMARY)))
                .allSatisfy(definition -> assertThat(definition.inputSchema())
                        .doesNotContain("title", "dueTimeStr"));
        assertThat(registry.execute(
                ToolNames.LIST_TODOS,
                "{}",
                runContext()).content()).contains("演示任务", "8月1日 18:00");
        assertThat(registry.execute(
                ToolNames.TODAY_TODOS,
                "{}",
                runContext()).content()).contains("演示任务", "18:00");
        assertThat(registry.execute(
                ToolNames.DAILY_SUMMARY,
                "{}",
                runContext()).content()).contains("今日要务", "演示任务");
    }

    @Test
    void noArgumentToolsIgnoreNoModelControlledIdentity() {
        NoArgumentsToolInput input = new NoArgumentsToolInput();

        assertThat(new ListTodosTool(pendingItemService, reminderState, reference).execute(input, context).content())
                .contains("当前没有待办事项");
    }

    @Test
    void listTodosIncludesLiveLearningTasksAfterLogin() {
        LearningCapability learning = mock(LearningCapability.class);
        CourseTask task = new CourseTask(
                "task-1", "第一章作业", TaskType.HOMEWORK,
                CompletionStatus.PENDING, Instant.parse("2026-08-02T10:00:00Z"),
                "course-1", "chaoxing", "");
        when(learning.fetchAllPendingCourseTasks()).thenReturn(List.of(
                new LearningCapability.EnrichedCourseTask(task, "高等数学")));
        PendingItemService service = new PendingItemService(
                List.of(
                        new PersonalTodoPendingAdapter(capability),
                        new CourseTaskPendingAdapter(learning)),
                Clock.fixed(
                        Instant.parse("2026-08-01T02:00:00Z"),
                        StudentTodoCapability.SHANGHAI));

        String content = new ListTodosTool(service, reminderState, reference)
                .execute(new NoArgumentsToolInput(), context)
                .content();

        assertThat(content).contains("高等数学 - 第一章作业", "学习通");
    }

    @Test
    void queryTodosFiltersLearningTasksByCustomTimeRange() {
        LearningCapability learning = mock(LearningCapability.class);
        CourseTask julyTask = new CourseTask(
                "task-july", "七月作业", TaskType.HOMEWORK,
                CompletionStatus.PENDING, Instant.parse("2026-07-20T02:00:00Z"),
                "course-1", "chaoxing", "");
        CourseTask augustTask = new CourseTask(
                "task-august", "八月作业", TaskType.HOMEWORK,
                CompletionStatus.PENDING, Instant.parse("2026-08-20T02:00:00Z"),
                "course-2", "chaoxing", "");
        when(learning.fetchAllPendingCourseTasks()).thenReturn(List.of(
                new LearningCapability.EnrichedCourseTask(julyTask, "七月课程"),
                new LearningCapability.EnrichedCourseTask(augustTask, "八月课程")));
        PendingItemService service = new PendingItemService(
                List.of(new CourseTaskPendingAdapter(learning)),
                Clock.fixed(
                        Instant.parse("2026-08-01T02:00:00Z"),
                        StudentTodoCapability.SHANGHAI));
        QueryTodosTool tool = new QueryTodosTool(service, reminderState, reference);

        String content = tool.execute(
                new TodoQueryInput(
                        TodoFilter.COURSE_ONLY,
                        "2026-08-01",
                        "2026-09-01",
                        "COURSE"),
                context)
                .content();

        assertThat(content).contains("八月作业").doesNotContain("七月作业");
    }

    @Test
    void listTodosDefaultWindowExcludesNoDeadlineCourseTasks() {
        LearningCapability learning = mock(LearningCapability.class);
        CourseTask soonTask = new CourseTask(
                "task-soon", "近期作业", TaskType.HOMEWORK,
                CompletionStatus.PENDING, Instant.parse("2026-08-11T02:00:00Z"),
                "course-1", "chaoxing", "");
        CourseTask noDeadlineTask = new CourseTask(
                "task-no-deadline", "无截止作业", TaskType.HOMEWORK,
                CompletionStatus.PENDING, Instant.MAX,
                "course-2", "chaoxing", "");
        when(learning.fetchAllPendingCourseTasks()).thenReturn(List.of(
                new LearningCapability.EnrichedCourseTask(soonTask, "近期课程"),
                new LearningCapability.EnrichedCourseTask(noDeadlineTask, "无截止课程")));
        PendingItemService service = new PendingItemService(
                List.of(new CourseTaskPendingAdapter(learning)),
                Clock.fixed(
                        Instant.parse("2026-08-01T02:00:00Z"),
                        StudentTodoCapability.SHANGHAI));

        String content = new ListTodosTool(service, reminderState, reference)
                .execute(new NoArgumentsToolInput(), context)
                .content();

        assertThat(content).contains("近期作业").doesNotContain("无截止作业");
    }

    private ToolRunContext runContext() {
        return new ToolRunContext(
                CONVERSATION,
                "演示待办",
                false,
                false,
                0,
                new ObjectMapper());
    }
}
