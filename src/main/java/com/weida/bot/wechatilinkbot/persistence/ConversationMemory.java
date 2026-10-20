package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.util.List;

/**
 * Stores and retrieves bounded conversational memory independently of its backend.
 */
public interface ConversationMemory {

    List<ConversationMessage> listRecent(String conversationId, int limit);

    String getSummary(String conversationId);

    void compact(String conversationId, String summary, int keepRecentMessages);

    void saveExchange(
            String conversationId,
            String userContent,
            String assistantContent);

    void clear(String conversationId);
}
