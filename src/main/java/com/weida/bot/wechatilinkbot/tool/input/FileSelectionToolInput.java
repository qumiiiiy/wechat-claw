package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/** Input containing an opaque ID previously returned by the file search capability. */
public record FileSelectionToolInput(
        @ToolParameter(description = "文件搜索结果返回的完整不透明 file ID")
        String fileId) {
}
