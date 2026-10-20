package com.weida.bot.wechatilinkbot.channel.ilink;

import com.weida.bot.wechatilinkbot.model.MediaPart;
import java.util.List;

/**
 * SDK-neutral content normalized from one inbound iLink message.
 *
 * @param prompt complete model prompt
 * @param fallback deterministic reply when the agent is unavailable
 * @param currentUserText direct user text used for intent detection
 * @param mediaParts bounded visual attachments
 */
record IlinkInboundMessage(
        String prompt,
        String fallback,
        String currentUserText,
        List<MediaPart> mediaParts) {

    IlinkInboundMessage {
        prompt = prompt == null ? "" : prompt;
        fallback = fallback == null ? "" : fallback;
        currentUserText = currentUserText == null ? "" : currentUserText;
        mediaParts = mediaParts == null ? List.of() : List.copyOf(mediaParts);
    }
}
