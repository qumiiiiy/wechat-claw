package com.weida.bot.wechatilinkbot.agent;

import java.util.List;

/**
 * One model response before the agent executes any requested tools.
 */
public record ModelResponse(String text, List<ModelToolCall> toolCalls) {

    public ModelResponse {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        if (text.isBlank() && toolCalls.isEmpty()) {
            throw new IllegalArgumentException("模型响应不能为空");
        }
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
