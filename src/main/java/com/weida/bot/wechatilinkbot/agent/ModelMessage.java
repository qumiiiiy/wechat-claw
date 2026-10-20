package com.weida.bot.wechatilinkbot.agent;

import com.weida.bot.wechatilinkbot.model.MediaPart;
import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral model message, including tool-loop protocol fields and tool media.
 */
public record ModelMessage(
        ModelRole role,
        String text,
        List<MediaPart> mediaParts,
        List<ModelToolCall> toolCalls,
        String toolCallId,
        String toolName) {

    public ModelMessage {
        Objects.requireNonNull(role, "role 不能为空");
        text = text == null ? "" : text;
        mediaParts = mediaParts == null ? List.of() : List.copyOf(mediaParts);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolCallId = toolCallId == null ? "" : toolCallId.strip();
        toolName = toolName == null ? "" : toolName.strip();
        validate(role, mediaParts, toolCalls, toolCallId, toolName);
    }

    public static ModelMessage system(String text) {
        return new ModelMessage(ModelRole.SYSTEM, text, List.of(), List.of(), "", "");
    }

    public static ModelMessage user(String text, List<MediaPart> mediaParts) {
        return new ModelMessage(ModelRole.USER, text, mediaParts, List.of(), "", "");
    }

    public static ModelMessage assistant(String text) {
        return new ModelMessage(ModelRole.ASSISTANT, text, List.of(), List.of(), "", "");
    }

    public static ModelMessage assistantToolCalls(
            String text,
            List<ModelToolCall> toolCalls) {
        return new ModelMessage(
                ModelRole.ASSISTANT,
                text,
                List.of(),
                toolCalls,
                "",
                "");
    }

    public static ModelMessage tool(
            String toolCallId,
            String toolName,
            String content) {
        return tool(toolCallId, toolName, content, List.of());
    }

    public static ModelMessage tool(
            String toolCallId,
            String toolName,
            String content,
            List<MediaPart> mediaParts) {
        return new ModelMessage(
                ModelRole.TOOL,
                content,
                mediaParts,
                List.of(),
                toolCallId,
                toolName);
    }

    private static void validate(
            ModelRole role,
            List<MediaPart> mediaParts,
            List<ModelToolCall> toolCalls,
            String toolCallId,
            String toolName) {
        if (role != ModelRole.USER && role != ModelRole.TOOL && !mediaParts.isEmpty()) {
            throw new IllegalArgumentException("只有用户消息或工具结果可以包含媒体");
        }
        if (role != ModelRole.ASSISTANT && !toolCalls.isEmpty()) {
            throw new IllegalArgumentException("只有助手消息可以包含工具调用");
        }
        if (role == ModelRole.TOOL && (toolCallId.isBlank() || toolName.isBlank())) {
            throw new IllegalArgumentException("工具结果必须包含调用 id 和工具名称");
        }
    }
}
