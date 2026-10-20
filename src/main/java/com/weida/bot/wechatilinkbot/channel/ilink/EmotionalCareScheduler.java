package com.weida.bot.wechatilinkbot.channel.ilink;

import com.weida.bot.wechatilinkbot.capability.EmotionalProfileService;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Computes bounded care content without attempting proactive WeChat sends. */
@Component
@EnableScheduling
@ConditionalOnProperty(
        prefix = "bot.capability.profile",
        name = "care-enabled",
        havingValue = "true")
public class EmotionalCareScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(EmotionalCareScheduler.class);
    private static final String CONVERSATION_PREFIX = "wechat-ilink|";
    private static final String NEGATIVE_MOOD_MARKER = "[MOOD: low]";

    private final IlinkPendingReplyStore pendingReplyStore;
    private final EmotionalProfileService emotionalProfileService;

    public EmotionalCareScheduler(
            IlinkPendingReplyStore pendingReplyStore,
            EmotionalProfileService emotionalProfileService) {
        this.pendingReplyStore = pendingReplyStore;
        this.emotionalProfileService = emotionalProfileService;
    }

    /** Queues at most one care message per conversation for a later reply window. */
    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    public void checkOnUsers() {
        Map<String, String> profiles = emotionalProfileService.getProfileSnapshot();
        if (profiles.isEmpty()) {
            LOG.debug("emotional_care_skip no_profiles");
            return;
        }
        int queued = 0;
        for (Map.Entry<String, String> profileEntry : profiles.entrySet()) {
            String conversationId = profileEntry.getKey();
            if (!isIlinkConversation(conversationId)
                    || !hasNegativeEmotion(profileEntry.getValue())) {
                continue;
            }
            if (pendingReplyStore.queue(conversationId, buildCareMessage())) {
                queued++;
            }
        }
        if (queued > 0) {
            LOG.info("emotional_care_replies_queued count={}", queued);
        }
    }

    private static boolean isIlinkConversation(String conversationId) {
        return conversationId != null
                && conversationId.startsWith(CONVERSATION_PREFIX)
                && conversationId.length() > CONVERSATION_PREFIX.length();
    }

    private static boolean hasNegativeEmotion(String profileText) {
        return profileText != null
                && profileText.contains(NEGATIVE_MOOD_MARKER);
    }

    private static String buildCareMessage() {
        return "嘿，我注意到你之前好像心情不太好。"
                + "想聊聊吗？我一直在的 ❤️";
    }
}
