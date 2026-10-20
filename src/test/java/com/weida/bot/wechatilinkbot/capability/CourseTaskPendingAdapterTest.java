package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.model.CourseTask;
import com.weida.bot.wechatilinkbot.model.CourseTask.CompletionStatus;
import com.weida.bot.wechatilinkbot.model.CourseTask.TaskType;
import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("学习通课程任务适配器")
class CourseTaskPendingAdapterTest {

    private LearningCapability learningCapability;
    private CourseTaskPendingAdapter adapter;

    @BeforeEach
    void setUp() {
        learningCapability = mock(LearningCapability.class);
        adapter = new CourseTaskPendingAdapter(learningCapability);
    }

    @Test
    @DisplayName("source 返回 COURSE")
    void sourceReturnsCourse() {
        assertThat(adapter.source()).isEqualTo(PendingItemSource.COURSE);
    }

    @Test
    @DisplayName("supportsModify 返回 false（只读）")
    void supportsModifyIsFalse() {
        assertThat(adapter.supportsModify()).isFalse();
    }

    @Test
    @DisplayName("有截止日期的课程任务被正确映射")
    void mapsTaskWithDeadline() {
        Instant deadline = Instant.parse("2026-08-10T12:00:00Z");
        CourseTask task = new CourseTask(
                "task-1", "第一章作业", TaskType.HOMEWORK,
                CompletionStatus.PENDING, deadline, "course-1", "chaoxing", "");
        when(learningCapability.fetchAllPendingCourseTasks()).thenReturn(List.of(
                new LearningCapability.EnrichedCourseTask(task, "高等数学")));

        List<PendingItem> items = adapter.fetchItems("any-conv");

        assertThat(items).hasSize(1);
        PendingItem item = items.get(0);
        assertThat(item.title()).isEqualTo("高等数学 - 第一章作业");
        assertThat(item.source()).isEqualTo(PendingItemSource.COURSE);
        assertThat(item.dueTime()).isEqualTo(deadline);
        assertThat(item.status()).isEqualTo(StudentTodoStatus.PENDING);
        assertThat(item.internalId()).isEqualTo(task.deduplicationKey());
    }

    @Test
    @DisplayName("无截止日期的课程任务使用 Instant.MAX")
    void mapsTaskWithoutDeadline() {
        CourseTask task = new CourseTask(
                "task-2", "无截止任务", TaskType.CHAPTER,
                CompletionStatus.PENDING, null, "course-2", "chaoxing", "");
        when(learningCapability.fetchAllPendingCourseTasks()).thenReturn(List.of(
                new LearningCapability.EnrichedCourseTask(task, "英语")));

        List<PendingItem> items = adapter.fetchItems("any-conv");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).dueTime()).isEqualTo(Instant.MAX);
    }

    @Test
    @DisplayName("空课程名时标题只显示任务名")
    void mapsTaskWithBlankCourseName() {
        CourseTask task = new CourseTask(
                "task-3", "独立任务", TaskType.EXAM,
                CompletionStatus.PENDING, Instant.parse("2026-08-15T10:00:00Z"),
                "course-3", "chaoxing", "");
        when(learningCapability.fetchAllPendingCourseTasks()).thenReturn(List.of(
                new LearningCapability.EnrichedCourseTask(task, "")));

        List<PendingItem> items = adapter.fetchItems("any-conv");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("独立任务");
    }

    @Test
    @DisplayName("fetchAllPendingCourseTasks 返回空时适配器也返回空")
    void emptyTasksReturnsEmptyList() {
        when(learningCapability.fetchAllPendingCourseTasks()).thenReturn(List.of());

        List<PendingItem> items = adapter.fetchItems("any-conv");

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("多个课程任务被全部映射")
    void mapsMultipleTasks() {
        CourseTask task1 = new CourseTask(
                "t1", "作业1", TaskType.HOMEWORK, CompletionStatus.PENDING,
                Instant.parse("2026-08-10T12:00:00Z"), "c1", "chaoxing", "");
        CourseTask task2 = new CourseTask(
                "t2", "考试1", TaskType.EXAM, CompletionStatus.PENDING,
                Instant.parse("2026-08-12T09:00:00Z"), "c2", "chaoxing", "");
        when(learningCapability.fetchAllPendingCourseTasks()).thenReturn(List.of(
                new LearningCapability.EnrichedCourseTask(task1, "数学"),
                new LearningCapability.EnrichedCourseTask(task2, "英语")));

        List<PendingItem> items = adapter.fetchItems("any-conv");

        assertThat(items).hasSize(2);
        assertThat(items).extracting(PendingItem::title)
                .containsExactly("数学 - 作业1", "英语 - 考试1");
    }
}