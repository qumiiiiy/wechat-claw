package com.weida.bot.wechatilinkbot.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import com.weida.bot.wechatilinkbot.persistence.mybatis.ConversationMessageMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;

class SqliteConversationMemoryTest {

    private ConversationMessageMapper mapper;
    private ConversationMemoryProperties properties;
    private SqliteConversationMemory memory;

    @BeforeEach
    void setUp() {
        mapper = mock(ConversationMessageMapper.class);
        properties = new ConversationMemoryProperties();
        properties.setRetainedMessages(4);
        memory = new SqliteConversationMemory(mapper, properties);
    }

    @Test
    void savesExchangeAndPrunesOlderMessagesInOrder() {
        when(mapper.getRetentionCutoffId("bot|user", 3)).thenReturn(12L);

        memory.saveExchange("bot|user", "你好", "你好，有什么可以帮你？");

        InOrder callOrder = inOrder(mapper);
        callOrder.verify(mapper).insert("bot|user", "user", "你好");
        callOrder.verify(mapper).insert("bot|user", "assistant", "你好，有什么可以帮你？");
        callOrder.verify(mapper).getRetentionCutoffId("bot|user", 3);
        callOrder.verify(mapper).deleteOlder("bot|user", 12L);
    }

    @Test
    void returnsAnImmutableMessageSnapshot() {
        Instant currentTime = Instant.now();
        List<ConversationMessage> mutableMessageList = new ArrayList<>();
        mutableMessageList.add(new ConversationMessage(
                1L,
                "bot|user",
                "user",
                "你好",
                currentTime,
                currentTime));
        when(mapper.listRecent("bot|user", 2)).thenReturn(mutableMessageList);

        List<ConversationMessage> result = memory.listRecent("bot|user", 2);

        assertThat(result).hasSize(1);
        assertThatThrownBy(() -> result.add(result.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(mapper).listRecent("bot|user", 2);
    }

    @Test
    void compactsOlderMessagesAndStoresSummary() {
        when(mapper.getRetentionCutoffId("bot|user", 1)).thenReturn(12L);

        memory.compact("bot|user", "用户偏好中文回复。", 2);

        InOrder callOrder = inOrder(mapper);
        callOrder.verify(mapper).getRetentionCutoffId("bot|user", 1);
        callOrder.verify(mapper).deleteOlder("bot|user", 12L);
        callOrder.verify(mapper).upsertSummary(
                eq("bot|user"),
                eq("用户偏好中文回复。"),
                org.mockito.ArgumentMatchers.any(Instant.class));
    }

    @Test
    void readsAndDeletesPersistedSummary() {
        when(mapper.findSummary("bot|user")).thenReturn("已确认的偏好");

        assertThat(memory.getSummary("bot|user")).isEqualTo("已确认的偏好");
        memory.clear("bot|user");

        verify(mapper).findSummary("bot|user");
        verify(mapper).deleteSummary("bot|user");
    }

    @Test
    void translatesMyBatisDataAccessFailures() {
        when(mapper.listRecent("bot|user", 2))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> memory.listRecent("bot|user", 2))
                .isInstanceOf(ConversationPersistenceException.class)
                .hasMessageContaining("读取");
    }

    @Test
    void prunesExpiredRowsAndClearsOneConversation() {
        properties.setExpireAfter(Duration.ofHours(24));
        when(mapper.listRecent("bot|user", 2)).thenReturn(List.of());

        memory.listRecent("bot|user", 2);
        memory.clear("bot|user");

        verify(mapper).deleteExpired(
                org.mockito.ArgumentMatchers.eq("bot|user"),
                org.mockito.ArgumentMatchers.any(Instant.class));
        verify(mapper).deleteExpiredSummary(
                org.mockito.ArgumentMatchers.eq("bot|user"),
                org.mockito.ArgumentMatchers.any(Instant.class));
        verify(mapper).deleteAll("bot|user");
        verify(mapper).deleteSummary("bot|user");
    }
}
