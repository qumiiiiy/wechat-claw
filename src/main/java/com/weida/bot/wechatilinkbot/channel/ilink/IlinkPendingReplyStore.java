package com.weida.bot.wechatilinkbot.channel.ilink;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Bounded in-memory content waiting for the user's next valid reply window. */
@Component
final class IlinkPendingReplyStore {

    private final IlinkProperties properties;
    private final ConcurrentHashMap<String, String> pendingByConversation =
            new ConcurrentHashMap<>();

    IlinkPendingReplyStore(IlinkProperties properties) {
        this.properties = properties;
    }

    synchronized boolean queue(String conversationId, String content) {
        String safeConversationId = requireText(conversationId, "conversationId");
        String safeContent = requireText(content, "content");
        if (safeContent.length() > properties.getMaxPendingReplyCharacters()) {
            safeContent = safeContent.substring(0, properties.getMaxPendingReplyCharacters());
        }
        if (!pendingByConversation.containsKey(safeConversationId)
                && pendingByConversation.size() >= properties.getMaxPendingReplies()) {
            return false;
        }
        return pendingByConversation.putIfAbsent(safeConversationId, safeContent) == null;
    }

    Optional<String> consume(String conversationId) {
        return Optional.ofNullable(pendingByConversation.remove(
                requireText(conversationId, "conversationId")));
    }

    int size() {
        return pendingByConversation.size();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.strip();
    }
}
