package com.weida.bot.wechatilinkbot.model;

import java.util.List;

/**
 * User-facing output produced by one serialized agent run.
 *
 * @param text final text
 * @param generatedMedia media ready for channel delivery
 */
public record AgentReply(String text, List<GeneratedMedia> generatedMedia) {

    public AgentReply {
        text = text == null ? "" : text.strip();
        generatedMedia = generatedMedia == null ? List.of() : List.copyOf(generatedMedia);
    }
}
