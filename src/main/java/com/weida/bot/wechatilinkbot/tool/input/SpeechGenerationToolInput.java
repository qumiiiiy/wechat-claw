package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/**
 * Strongly typed speech-generation tool input.
 *
 * @param text text to synthesize
 * @param voice requested voice category
 * @author ryx
 * @since 2026-07-23
 */
public record SpeechGenerationToolInput(
        @ToolParameter(description = "需要直接朗读并合成为 MP4 的文字") String text,
        @ToolParameter(
                required = false,
                description = "音色类别：default、male 或 female；未指定时为 default")
        String voice) {
}
