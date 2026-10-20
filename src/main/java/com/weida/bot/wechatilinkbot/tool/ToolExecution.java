package com.weida.bot.wechatilinkbot.tool;

import com.weida.bot.wechatilinkbot.model.GeneratedMedia;

/**
 * Normalized result returned by one tool invocation.
 *
 * @param content JSON or text content returned to the model
 * @param generatedMedia optional media returned to the channel
 */
public record ToolExecution(String content, GeneratedMedia generatedMedia) {

    public ToolExecution {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 不能为空");
        }
        content = content.strip();
    }
}
