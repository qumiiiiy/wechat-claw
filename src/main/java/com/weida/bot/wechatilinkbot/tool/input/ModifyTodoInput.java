package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/** Input for modifying one todo's due time via natural reference. */
public record ModifyTodoInput(
        @ToolParameter(description = "待办引用，如 TD-xxx、第2项、第一个、刚才的")
        String todoId,

        @ToolParameter(description = "新的截止时间，如「明天18:00」「8月10日09:00」")
        String newDueTimeStr) {
}