package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/** Input identifying one server-created desktop task. */
public record DesktopTaskIdInput(
        @ToolParameter(description = "此前由 request_open_url 返回的任务 ID") String taskId) {
}
