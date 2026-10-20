package com.weida.bot.wechatilinkbot.tool;

/**
 * Model-visible tool definition.
 *
 * @param name unique function name
 * @param description usage description
 * @param inputSchema JSON Schema for arguments
 * @param returnDirect whether execution bypasses a follow-up model call
 */
public record ToolDefinition(
        String name,
        String description,
        String inputSchema,
        boolean returnDirect) {

    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("工具说明不能为空");
        }
        if (inputSchema == null || inputSchema.isBlank()) {
            throw new IllegalArgumentException("工具输入 Schema 不能为空");
        }
        name = name.strip();
        description = description.strip();
        inputSchema = inputSchema.strip();
    }
}
