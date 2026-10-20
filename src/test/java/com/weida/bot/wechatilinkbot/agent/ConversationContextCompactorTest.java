package com.weida.bot.wechatilinkbot.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import com.weida.bot.wechatilinkbot.persistence.ConversationMemory;
import com.weida.bot.wechatilinkbot.persistence.ConversationMemoryProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationContextCompactorTest {

    private static final String CONVERSATION_ID = "wechat-ilink|bot=bot-1|user=user-1";

    private AgentProperties agentProperties;
    private AgentModelClient modelClient;
    private ConversationMemory memory;
    private ConversationContextCompactor compactor;

    @BeforeEach
    void setUp() {
        agentProperties = new AgentProperties();
        agentProperties.setHistoryMessages(2);
        agentProperties.setContextCompressionKeepMessages(2);
        agentProperties.setMaxHistoryCharacters(1_000);
        agentProperties.setContextSummaryMaxCharacters(100);
        modelClient = mock(AgentModelClient.class);
        when(modelClient.available()).thenReturn(true);
        memory = mock(ConversationMemory.class);
        ConversationMemoryProperties memoryProperties = new ConversationMemoryProperties();
        memoryProperties.setRetainedMessages(20);
        compactor = new ConversationContextCompactor(
                agentProperties,
                modelClient,
                memory,
                memoryProperties);
    }

    @Test
    void usesLosslessTranscriptBeforeCallingTheModel() throws IOException {
        when(memory.listRecent(CONVERSATION_ID, 20)).thenReturn(List.of(
                message("user", "旧问题  保留原文"),
                message("assistant", "旧回答\n保留换行"),
                message("user", "最近问题"),
                message("assistant", "最近回答")));

        List<ConversationMessage> result = compactor.load(CONVERSATION_ID);

        assertThat(result).extracting(ConversationMessage::role)
                .containsExactly("history", "user", "assistant");
        assertThat(result.getFirst().content())
                .contains("用户：旧问题  保留原文", "助手：旧回答\n保留换行");
        assertThat(result).extracting(ConversationMessage::content)
                .contains("最近问题", "最近回答");
        verify(modelClient, never()).complete(any(ModelRequest.class));
        verify(memory, never()).compact(any(), any(), any(int.class));
    }

    @Test
    void callsModelOnlyWhenLosslessTranscriptDoesNotFitAndKeepsRecentMessages()
            throws IOException {
        agentProperties.setMaxHistoryCharacters(180);
        agentProperties.setContextSummaryMaxCharacters(40);
        List<ConversationMessage> history = List.of(
                message("user", "旧消息".repeat(30)),
                message("assistant", "旧回答".repeat(30)),
                message("user", "最近问题"),
                message("assistant", "最近回答"));
        when(memory.listRecent(CONVERSATION_ID, 20)).thenReturn(history);
        when(modelClient.complete(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("用户偏好中文，旧事项未完成。", List.of()));

        List<ConversationMessage> result = compactor.load(CONVERSATION_ID);

        assertThat(result).extracting(ConversationMessage::role)
                .containsExactly("summary", "user", "assistant");
        assertThat(result.getFirst().content()).contains("用户偏好中文");
        assertThat(result).extracting(ConversationMessage::content)
                .contains("最近问题", "最近回答");
        verify(memory).compact(
                CONVERSATION_ID,
                "用户偏好中文，旧事项未完成。",
                2);
        org.mockito.ArgumentCaptor<ModelRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(ModelRequest.class);
        verify(modelClient).complete(requestCaptor.capture());
        assertThat(requestCaptor.getValue().tools()).isEmpty();
        assertThat(requestCaptor.getValue().toolRequired()).isFalse();
        assertThat(requestCaptor.getValue().messages().getLast().text())
                .contains("旧消息", "旧回答");
    }

    @Test
    void fallsBackToRecentRawMessagesWhenSummaryFails() throws IOException {
        agentProperties.setMaxHistoryCharacters(180);
        agentProperties.setContextSummaryMaxCharacters(40);
        when(memory.listRecent(CONVERSATION_ID, 20)).thenReturn(List.of(
                message("user", "旧消息".repeat(30)),
                message("assistant", "旧回答".repeat(30)),
                message("user", "最近问题"),
                message("assistant", "最近回答")));
        when(modelClient.complete(any(ModelRequest.class)))
                .thenThrow(new IOException("provider unavailable"));

        List<ConversationMessage> result = compactor.load(CONVERSATION_ID);

        assertThat(result).extracting(ConversationMessage::content)
                .containsExactly("最近问题", "最近回答");
        verify(memory, never()).compact(any(), any(), any(int.class));
    }

    @Test
    void includesPersistedSummaryWithoutReSummarizingWhenItFits() throws IOException {
        when(memory.listRecent(CONVERSATION_ID, 20)).thenReturn(List.of(
                message("user", "最近问题"),
                message("assistant", "最近回答")));
        when(memory.getSummary(CONVERSATION_ID)).thenReturn("已经确认使用中文回复。");

        List<ConversationMessage> result = compactor.load(CONVERSATION_ID);

        assertThat(result).extracting(ConversationMessage::role)
                .containsExactly("summary", "user", "assistant");
        assertThat(result.getFirst().content()).isEqualTo("已经确认使用中文回复。");
        verify(modelClient, never()).complete(any(ModelRequest.class));
    }

    private static ConversationMessage message(String role, String content) {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        return new ConversationMessage(1L, CONVERSATION_ID, role, content, now, now);
    }
}
