package com.weida.bot.wechatilinkbot.agent;

import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import java.util.List;

/**
 * One provider request within an agent-owned tool loop.
 */
public record ModelRequest(
        List<ModelMessage> messages,
        List<ToolDefinition> tools,
        boolean toolRequired) {

    public ModelRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("模型消息不能为空");
        }
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (toolRequired && tools.isEmpty()) {
            throw new IllegalArgumentException("要求工具调用时必须提供工具");
        }
    }
}
