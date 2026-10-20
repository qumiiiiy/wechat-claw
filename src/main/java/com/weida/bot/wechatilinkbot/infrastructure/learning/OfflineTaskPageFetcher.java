package com.weida.bot.wechatilinkbot.infrastructure.learning;

import java.util.Collections;
import java.util.Map;

/**
 * 离线任务页面获取器。
 * 仅返回合成内容，不访问网络，用于测试。
 */
public class OfflineTaskPageFetcher implements TaskPageFetcher {

    private final Map<String, String> taskListHtmls;
    private final Map<String, String> coursePageHtmls;
    private final String courseListHtml;

    /**
     * 创建离线获取器。
     *
     * @param taskListHtmls courseId -> 任务列表 HTML 的映射
     * @param coursePageHtmls "courseId:classId" -> 课程页面 HTML 的映射
     * @param courseListHtml 课程列表 HTML
     */
    public OfflineTaskPageFetcher(
            Map<String, String> taskListHtmls,
            Map<String, String> coursePageHtmls,
            String courseListHtml) {
        this.taskListHtmls = taskListHtmls != null ? taskListHtmls : Collections.emptyMap();
        this.coursePageHtmls = coursePageHtmls != null ? coursePageHtmls : Collections.emptyMap();
        this.courseListHtml = courseListHtml;
    }

    @Override
    public String fetchTaskListHtml(String courseId, String classId, String cpi, String enc) {
        if (courseId == null) {
            return null;
        }
        return taskListHtmls.get(courseId.strip());
    }

    @Override
    public String fetchCourseListHtml() {
        return courseListHtml;
    }

    @Override
    public String fetchCoursePageHtml(String courseId, String classId) {
        if (courseId == null || classId == null) {
            return null;
        }
        String key = courseId.strip() + ":" + classId.strip();
        return coursePageHtmls.get(key);
    }
}
