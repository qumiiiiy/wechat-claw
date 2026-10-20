package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/**
 * Strongly typed image-generation tool input.
 *
 * @param prompt complete image-generation prompt
 * @author ryx
 * @since 2026-07-23
 */
public record ImageGenerationToolInput(
        @ToolParameter(description = "用于图片生成模型的完整、具体中文提示词") String prompt) {
}
