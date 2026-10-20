package com.weida.bot.wechatilinkbot.model;

import java.util.List;

/**
 * Provider-neutral input for one agent run.
 *
 * @param conversationId stable channel conversation identity
 * @param prompt normalized prompt assembled by the channel adapter
 * @param currentUserText current inbound user text without reference expansions
 * @param mediaParts trusted, bounded visual attachments
 */
public record AgentRequest(
        String conversationId,
        String prompt,
        String currentUserText,
        List<MediaPart> mediaParts) {

    private static final String DEFAULT_PROMPT = "请根据我发送的内容作出回应。";

    public AgentRequest {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        conversationId = conversationId.strip();
        prompt = normalize(prompt);
        currentUserText = normalize(currentUserText);
        mediaParts = mediaParts == null ? List.of() : List.copyOf(mediaParts);
    }

    public AgentRequest(String conversationId, String prompt, List<MediaPart> mediaParts) {
        this(conversationId, prompt, prompt, mediaParts);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? DEFAULT_PROMPT : value.strip();
    }
}
