package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/**
 * Strongly typed in-memory text-file tool input.
 *
 * @param fileName safe file name including a supported extension
 * @param content complete UTF-8 file content
 * @author ryx
 * @since 2026-07-23
 */
public record TextFileGenerationToolInput(
        @ToolParameter(description = "安全文件名，必须含受支持扩展名且不能包含路径")
        String fileName,
        @ToolParameter(description = "要写入文件的完整 UTF-8 正文")
        String content) {
}
