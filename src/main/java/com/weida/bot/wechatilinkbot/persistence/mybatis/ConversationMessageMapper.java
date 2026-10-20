package com.weida.bot.wechatilinkbot.persistence.mybatis;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for conversation messages.
 */
public interface ConversationMessageMapper {

    List<ConversationMessage> listRecent(
            @Param("conversationId") String conversationId,
            @Param("limit") int limit);

    String findSummary(@Param("conversationId") String conversationId);

    void upsertSummary(
            @Param("conversationId") String conversationId,
            @Param("summary") String summary,
            @Param("updatedAt") Instant updatedAt);

    void insert(
            @Param("conversationId") String conversationId,
            @Param("role") String role,
            @Param("content") String content);

    Long getRetentionCutoffId(
            @Param("conversationId") String conversationId,
            @Param("offset") int offset);

    int deleteOlder(
            @Param("conversationId") String conversationId,
            @Param("cutoffId") Long cutoffId);

    int deleteExpired(
            @Param("conversationId") String conversationId,
            @Param("createdBefore") Instant createdBefore);

    int deleteExpiredSummary(
            @Param("conversationId") String conversationId,
            @Param("updatedBefore") Instant updatedBefore);

    int deleteSummary(@Param("conversationId") String conversationId);

    int deleteAll(@Param("conversationId") String conversationId);
}
