package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/** Input identifying one server-created OCS study session. */
public record OcsStudySessionIdInput(
        @ToolParameter(description = "此前 start_ocs_study 返回的 OCS 学习会话 ID")
        String sessionId) {
}
