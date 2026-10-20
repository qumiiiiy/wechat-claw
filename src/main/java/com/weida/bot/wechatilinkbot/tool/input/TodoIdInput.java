package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/** Input for completing or querying one student todo by ID. */
public record TodoIdInput(
        @ToolParameter(description = "待办 ID，格式为 TD-{12 位十六进制字符}")
        String todoId) {
}
