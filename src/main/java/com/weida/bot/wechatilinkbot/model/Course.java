package com.weida.bot.wechatilinkbot.model;

import java.util.Objects;

/**
 * 学习通课程数据结构。
 * 跨模块稳定数据，不依赖其他业务模块。
 */
public record Course(
    String courseId,
    String courseName,
    String teacherName,
    String schoolName,
    String courseUrl
) {
    public Course {
        Objects.requireNonNull(courseId, "courseId must not be null");
        Objects.requireNonNull(courseName, "courseName must not be null");
        courseId = courseId.strip();
        courseName = courseName.strip();
        if (courseId.isEmpty()) {
            throw new IllegalArgumentException("courseId must not be empty");
        }
        if (courseName.isEmpty()) {
            throw new IllegalArgumentException("courseName must not be empty");
        }
        teacherName = teacherName != null ? teacherName.strip() : "";
        schoolName = schoolName != null ? schoolName.strip() : "";
        courseUrl = courseUrl != null ? courseUrl.strip() : "";
    }
}
