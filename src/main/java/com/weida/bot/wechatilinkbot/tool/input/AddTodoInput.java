package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/** Input for creating one student todo reminder. */
public record AddTodoInput(
        @ToolParameter(description = "待办标题，如「数学作业」「英语阅读」，不超过 200 字符")
        String title,

        @ToolParameter(description = "明确的截止时间表述，如「明天18:00」「8月5日09:00」「下周一14:00」。"
                + "必须是确定的日期+时间，不能是模糊表述如「有空的时候」「尽快」")
        String dueTimeStr) {
}
