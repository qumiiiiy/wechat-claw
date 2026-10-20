package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/** Input for a request to open one validated local HTTPS URL. */
public record OpenUrlTaskInput(
        @ToolParameter(description = "必须是用户明确提供的 HTTPS 网址；明确请求打开 QQ 邮箱时使用 "
                + "https://mail.qq.com/；不能在网址中嵌入账号信息")
        String url) {
}
