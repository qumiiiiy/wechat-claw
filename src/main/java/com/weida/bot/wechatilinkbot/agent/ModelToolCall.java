package com.weida.bot.wechatilinkbot.agent;

/**
 * Provider-neutral function call requested by a model.
 *
 * @param id provider call identifier
 * @param name authorized tool name
 * @param arguments JSON arguments
 */
public record ModelToolCall(String id, String name, String arguments) {

    public ModelToolCall {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("工具调用 id 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具调用名称不能为空");
        }
        id = id.strip();
        name = name.strip();
        arguments = arguments == null || arguments.isBlank() ? "{}" : arguments.strip();
    }
}
