package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;
import java.util.List;

/**
 * Strongly typed two-stage email tool input.
 *
 * @param action prepare, confirm, or clarify
 * @param recipientList recipients used only by prepare
 * @param subject subject used only by prepare
 * @param body plain-text body used only by prepare
 * @author ryx
 * @since 2026-07-23
 */
public record EmailToolInput(
        @ToolParameter(description = "操作：prepare、confirm 或 clarify") String action,
        @ToolParameter(required = false, description = "prepare 时使用的主收件人邮箱列表")
        List<String> recipientList,
        @ToolParameter(required = false, description = "prepare 时使用的邮件主题")
        String subject,
        @ToolParameter(required = false, description = "prepare 时使用的纯文本正文")
        String body) {

    public EmailToolInput {
        recipientList = recipientList == null ? List.of() : List.copyOf(recipientList);
    }
}
