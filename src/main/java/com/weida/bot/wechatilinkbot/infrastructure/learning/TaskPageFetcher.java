package com.weida.bot.wechatilinkbot.infrastructure.learning;

/**
 * 学习通页面获取端口。
 * 分离网络获取与解析逻辑，支持离线测试。
 */
public interface TaskPageFetcher {

    /**
     * 获取任务列表页面 HTML。
     *
     * @param courseId 课程 ID
     * @param classId 班级 ID
     * @param cpi 用户 cpi
     * @param enc 加密参数
     * @return 任务列表页面 HTML，获取失败返回 null 或空字符串
     */
    String fetchTaskListHtml(String courseId, String classId, String cpi, String enc);

    /**
     * 获取课程列表页面 HTML。
     *
     * @return 课程列表页面 HTML，获取失败返回 null 或空字符串
     */
    String fetchCourseListHtml();

    /**
     * 获取课程详情页面 HTML（用于提取 enc 参数）。
     *
     * @param courseId 课程 ID
     * @param classId 班级 ID
     * @return 课程详情页面 HTML，获取失败返回 null 或空字符串
     */
    String fetchCoursePageHtml(String courseId, String classId);
}
