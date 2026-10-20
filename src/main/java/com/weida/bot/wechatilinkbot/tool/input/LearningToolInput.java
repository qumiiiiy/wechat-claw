package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/**
 * 学习通查询工具输入。
 */
public record LearningToolInput(
    @ToolParameter(description = "操作类型：login、courses、details、tasks、tasks_by_name、pending")
    String action,

    @ToolParameter(description = "课程 ID（查询任务列表时需要）", required = false)
    String courseId,

    @ToolParameter(description = "班级 ID（查询任务列表时需要）", required = false)
    String classId,

    @ToolParameter(description = "加密参数（查询任务列表时需要）", required = false)
    String enc,

    @ToolParameter(description = "课程名称；按名称查询详情或任务时使用，支持包含匹配", required = false)
    String courseName,

    @ToolParameter(description = "课程列表中的引用，例如 C1；用户说第一门课时使用 C1", required = false)
    String courseReference
) {
    public LearningToolInput {
        action = action != null ? action.strip() : "courses";
        courseId = courseId != null ? courseId.strip() : "";
        classId = classId != null ? classId.strip() : "";
        enc = enc != null ? enc.strip() : "";
        courseName = courseName != null ? courseName.strip() : "";
        courseReference = courseReference != null ? courseReference.strip() : "";
    }
}
