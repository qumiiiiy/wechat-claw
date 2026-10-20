package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.weida.bot.wechatilinkbot.model.CourseTask;
import com.weida.bot.wechatilinkbot.model.CourseTask.CompletionStatus;
import com.weida.bot.wechatilinkbot.model.CourseTask.TaskType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 学习通数据去重工具测试。
 */
class LearningDeduplicatorTest {

    @Test
    void deduplicateTasksByDeduplicationKey() {
        CourseTask task1 = new CourseTask(
                "50000001", "作业一", TaskType.HOMEWORK, CompletionStatus.COMPLETED,
                null, "200000001", "chaoxing", "");
        CourseTask task2 = new CourseTask(
                "50000001", "作业一（重复）", TaskType.HOMEWORK, CompletionStatus.PENDING,
                null, "200000001", "chaoxing", "");
        CourseTask task3 = new CourseTask(
                "50000002", "作业二", TaskType.HOMEWORK, CompletionStatus.PENDING,
                null, "200000001", "chaoxing", "");

        List<CourseTask> tasks = List.of(task1, task2, task3);
        List<CourseTask> deduplicated = LearningDeduplicator.deduplicateTasks(tasks);

        assertThat(deduplicated).hasSize(2);
        assertThat(deduplicated.get(0).taskName()).isEqualTo("作业一");
        assertThat(deduplicated.get(1).taskName()).isEqualTo("作业二");
    }

    @Test
    void deduplicateTasksDifferentSources() {
        CourseTask task1 = new CourseTask(
                "50000001", "作业一", TaskType.HOMEWORK, CompletionStatus.COMPLETED,
                null, "200000001", "chaoxing", "");
        CourseTask task2 = new CourseTask(
                "50000001", "作业一", TaskType.HOMEWORK, CompletionStatus.COMPLETED,
                null, "200000001", "offline", "");

        List<CourseTask> tasks = List.of(task1, task2);
        List<CourseTask> deduplicated = LearningDeduplicator.deduplicateTasks(tasks);

        // 不同来源，不去重
        assertThat(deduplicated).hasSize(2);
    }

    @Test
    void deduplicateTasksEmptyList() {
        List<CourseTask> deduplicated = LearningDeduplicator.deduplicateTasks(List.of());
        assertThat(deduplicated).isEmpty();
    }

    @Test
    void deduplicateTasksNullList() {
        List<CourseTask> deduplicated = LearningDeduplicator.deduplicateTasks(null);
        assertThat(deduplicated).isEmpty();
    }

    @Test
    void deduplicateTasksPreservesOrder() {
        CourseTask task1 = new CourseTask(
                "50000002", "作业二", TaskType.HOMEWORK, CompletionStatus.PENDING,
                null, "200000001", "chaoxing", "");
        CourseTask task2 = new CourseTask(
                "50000001", "作业一", TaskType.HOMEWORK, CompletionStatus.COMPLETED,
                null, "200000001", "chaoxing", "");

        List<CourseTask> tasks = List.of(task1, task2);
        List<CourseTask> deduplicated = LearningDeduplicator.deduplicateTasks(tasks);

        assertThat(deduplicated).hasSize(2);
        assertThat(deduplicated.get(0).taskId()).isEqualTo("50000002");
        assertThat(deduplicated.get(1).taskId()).isEqualTo("50000001");
    }
}
