package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/** Input for one explicitly requested OCS-assisted study session. */
public record StartOcsStudyInput(
        @ToolParameter(description = "用户要学习的课程名称")
        String courseName,

        @ToolParameter(
                description = "最近一次课程列表或详情中的 C1、C17 等课程引用；存在时必须原样传入",
                required = false)
        String courseReference,

        @ToolParameter(
                description = "用户指定要打开的具体章节，例如「第三章」「绪论」「第一章 概述」；"
                        + "未指定时由伴随脚本自动打开第一个可学章节",
                required = false)
        String chapterName) {

    public StartOcsStudyInput(String courseName, String courseReference) {
        this(courseName, courseReference, "");
    }

    public StartOcsStudyInput {
        courseName = courseName == null ? "" : courseName.strip();
        courseReference = courseReference == null ? "" : courseReference.strip().toUpperCase();
        chapterName = chapterName == null ? "" : chapterName.strip();
    }
}
