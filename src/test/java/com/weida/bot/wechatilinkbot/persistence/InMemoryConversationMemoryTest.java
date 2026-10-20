package com.weida.bot.wechatilinkbot.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InMemoryConversationMemoryTest {

    @Test
    void retainsRecentMessagesInChronologicalOrder() {
        ConversationMemoryProperties properties = new ConversationMemoryProperties();
        properties.setRetainedMessages(4);
        InMemoryConversationMemory memory = new InMemoryConversationMemory(properties);

        memory.saveExchange("bot|user", "user-1", "assistant-1");
        memory.saveExchange("bot|user", "user-2", "assistant-2");
        memory.saveExchange("bot|user", "user-3", "assistant-3");

        assertThat(memory.listRecent("bot|user", 10))
                .extracting(ConversationMessage::content)
                .containsExactly("user-2", "assistant-2", "user-3", "assistant-3");
        assertThat(memory.listRecent("bot|user", 2))
                .extracting(ConversationMessage::content)
                .containsExactly("user-3", "assistant-3");
        assertThat(memory.listRecent("bot|user", 2))
                .allSatisfy(message ->
                        assertThat(message.conversationId()).isEqualTo("bot|user"));
        assertThat(memory.listRecent("bot|user", 2))
                .extracting(ConversationMessage::role)
                .containsExactly("user", "assistant");
        assertThat(memory.listRecent("bot|user", 2))
                .extracting(ConversationMessage::id)
                .isSorted();
    }

    @Test
    void clearsAndExpiresConversationMemory() {
        ConversationMemoryProperties properties = new ConversationMemoryProperties();
        AtomicReference<Instant> currentTime = new AtomicReference<>(
                Instant.parse("2026-07-21T00:00:00Z"));
        InMemoryConversationMemory memory =
                new InMemoryConversationMemory(properties) {
                    @Override
                    Instant currentTime() {
                        return currentTime.get();
                    }
                };
        memory.saveExchange("bot|clear", "user", "assistant");
        memory.compact("bot|clear", "已压缩摘要", 0);

        memory.clear("bot|clear");

        assertThat(memory.listRecent("bot|clear", 10)).isEmpty();
        assertThat(memory.getSummary("bot|clear")).isEmpty();

        properties.setExpireAfter(Duration.ofHours(1));
        memory.saveExchange("bot|expire", "user", "assistant");
        memory.compact("bot|expire", "已压缩摘要", 0);
        currentTime.set(currentTime.get().plus(Duration.ofHours(2)));

        assertThat(memory.listRecent("bot|expire", 10)).isEmpty();
        assertThat(memory.getSummary("bot|expire")).isEmpty();
    }
}
