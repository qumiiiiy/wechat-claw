package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.weida.bot.wechatilinkbot.model.Course;
import com.weida.bot.wechatilinkbot.model.CourseTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习通数据去重工具。
 * 课程按课程 ID 去重，任务按"来源 + 课程 ID + 任务 ID"去重。
 */
public final class LearningDeduplicator {

    private LearningDeduplicator() {
    }

    /**
     * 课程列表去重，保留首次出现的条目。
     *
     * @param courses 原始课程列表
     * @return 去重后的课程列表
     */
    public static List<Course> deduplicateCourses(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }

        Map<String, Course> seen = new LinkedHashMap<>();
        for (Course course : courses) {
            seen.putIfAbsent(course.courseId(), course);
        }
        return List.copyOf(seen.values());
    }

    /**
     * 任务列表去重，按"来源 + 课程 ID + 任务 ID"去重，保留首次出现的条目。
     *
     * @param tasks 原始任务列表
     * @return 去重后的任务列表
     */
    public static List<CourseTask> deduplicateTasks(List<CourseTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        Map<String, CourseTask> seen = new LinkedHashMap<>();
        for (CourseTask task : tasks) {
            seen.putIfAbsent(task.deduplicationKey(), task);
        }
        return List.copyOf(seen.values());
    }
}
