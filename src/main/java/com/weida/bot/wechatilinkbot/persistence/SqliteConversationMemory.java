package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import com.weida.bot.wechatilinkbot.persistence.mybatis.ConversationMessageMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional SQLite conversation storage.
 *
 * <p>This class is instantiated programmatically — it is not a Spring
 * component. The {@code ConversationMemory} bean is created in
 * {@code ConversationMemoryConfiguration}.</p>
 */
public class SqliteConversationMemory implements ConversationMemory {

    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final int MAX_CONVERSATION_ID_LENGTH = 255;

    private final ConversationMessageMapper mapper;
    private final ConversationMemoryProperties properties;

    public SqliteConversationMemory(
            ConversationMessageMapper mapper,
            ConversationMemoryProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public List<ConversationMessage> listRecent(String conversationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String safeConversationId = requireConversationId(conversationId);
        try {
            pruneExpired(safeConversationId);
            List<ConversationMessage> messageList = mapper.listRecent(safeConversationId, limit);
            return messageList == null ? List.of() : List.copyOf(messageList);
        } catch (DataAccessException exception) {
            throw storageFailure("读取", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public String getSummary(String conversationId) {
        String safeConversationId = requireConversationId(conversationId);
        try {
            pruneExpired(safeConversationId);
            String summary = mapper.findSummary(safeConversationId);
            return summary == null ? "" : summary;
        } catch (DataAccessException exception) {
            throw storageFailure("读取摘要", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public void compact(
            String conversationId,
            String summary,
            int keepRecentMessages) {
        String safeConversationId = requireConversationId(conversationId);
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("会话摘要不能为空");
        }
        if (keepRecentMessages < 0) {
            throw new IllegalArgumentException("保留消息数不能为负数");
        }
        try {
            pruneExpired(safeConversationId);
            if (keepRecentMessages == 0) {
                mapper.deleteAll(safeConversationId);
            } else {
                Long cutoffId = mapper.getRetentionCutoffId(
                        safeConversationId,
                        keepRecentMessages - 1);
                if (cutoffId != null) {
                    mapper.deleteOlder(safeConversationId, cutoffId);
                }
            }
            mapper.upsertSummary(
                    safeConversationId,
                    truncate(summary),
                    Instant.now());
        } catch (DataAccessException exception) {
            throw storageFailure("压缩摘要", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public void saveExchange(
            String conversationId,
            String userContent,
            String assistantContent) {
        String safeConversationId = requireConversationId(conversationId);
        try {
            pruneExpired(safeConversationId);
            mapper.insert(safeConversationId, USER_ROLE, truncate(userContent));
            mapper.insert(safeConversationId, ASSISTANT_ROLE, truncate(assistantContent));
            prune(safeConversationId);
        } catch (DataAccessException exception) {
            throw storageFailure("写入", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public void clear(String conversationId) {
        String safeConversationId = requireConversationId(conversationId);
        try {
            mapper.deleteAll(safeConversationId);
            mapper.deleteSummary(safeConversationId);
        } catch (DataAccessException exception) {
            throw storageFailure("清空", exception);
        }
    }

    private void pruneExpired(String conversationId) {
        if (properties.expirationEnabled()) {
            Instant cutoffTime = Instant.now().minus(properties.getExpireAfter());
            mapper.deleteExpired(conversationId, cutoffTime);
            mapper.deleteExpiredSummary(conversationId, cutoffTime);
        }
    }

    private void prune(String conversationId) {
        Long cutoffId = mapper.getRetentionCutoffId(
                conversationId,
                properties.getRetainedMessages() - 1);
        if (cutoffId != null) {
            mapper.deleteOlder(conversationId, cutoffId);
        }
    }

    private String truncate(String content) {
        String safeContent = content == null ? "" : content;
        int maxCharacters = properties.getMaxMessageCharacters();
        return safeContent.length() <= maxCharacters
                ? safeContent
                : safeContent.substring(0, maxCharacters) + "…";
    }

    private static String requireConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        String safeConversationId = conversationId.strip();
        if (safeConversationId.length() > MAX_CONVERSATION_ID_LENGTH) {
            throw new IllegalArgumentException("conversationId 长度不能超过 255");
        }
        return safeConversationId;
    }

    private static ConversationPersistenceException storageFailure(
            String operation,
            DataAccessException exception) {
        return new ConversationPersistenceException("SQLite 会话存储" + operation + "失败", exception);
    }
}
