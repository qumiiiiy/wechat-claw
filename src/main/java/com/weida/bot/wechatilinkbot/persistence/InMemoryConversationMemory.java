package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe bounded in-memory conversation storage for local development.
 *
 * <p>This class is instantiated programmatically — it is not a Spring
 * component. The {@code ConversationMemory} bean is created in
 * {@code ConversationMemoryConfiguration}.</p>
 */
public class InMemoryConversationMemory implements ConversationMemory {

    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";

    private final ConversationMemoryProperties properties;
    private final ConcurrentHashMap<String, Deque<ConversationMessage>> conversationMap =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SummaryEntry> summaryMap =
            new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public InMemoryConversationMemory(ConversationMemoryProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<ConversationMessage> listRecent(String conversationId, int limit) {
        Deque<ConversationMessage> messageQueue = conversationMap.get(conversationId);
        if (messageQueue == null || limit <= 0) {
            return List.of();
        }
        synchronized (messageQueue) {
            removeExpired(messageQueue, currentTime());
            List<ConversationMessage> snapshot = new ArrayList<>(messageQueue);
            int fromIndex = Math.max(0, snapshot.size() - limit);
            return List.copyOf(snapshot.subList(fromIndex, snapshot.size()));
        }
    }

    @Override
    public String getSummary(String conversationId) {
        String safeConversationId = requireConversationId(conversationId);
        SummaryEntry entry = summaryMap.get(safeConversationId);
        if (entry == null) {
            return "";
        }
        Instant currentTime = currentTime();
        if (properties.expirationEnabled()
                && entry.updatedAt().isBefore(currentTime.minus(properties.getExpireAfter()))) {
            summaryMap.remove(safeConversationId, entry);
            return "";
        }
        return entry.summary();
    }

    @Override
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
        Deque<ConversationMessage> messageQueue = conversationMap.get(safeConversationId);
        Instant currentTime = currentTime();
        if (messageQueue != null) {
            synchronized (messageQueue) {
                removeExpired(messageQueue, currentTime);
                while (messageQueue.size() > keepRecentMessages) {
                    messageQueue.removeFirst();
                }
            }
        }
        summaryMap.put(
                safeConversationId,
                new SummaryEntry(truncate(summary), currentTime));
    }

    @Override
    public void saveExchange(
            String conversationId,
            String userContent,
            String assistantContent) {
        Deque<ConversationMessage> messageQueue = conversationMap.computeIfAbsent(
                conversationId,
                ignored -> new ArrayDeque<>());
        synchronized (messageQueue) {
            Instant currentTime = currentTime();
            removeExpired(messageQueue, currentTime);
            messageQueue.addLast(message(
                    conversationId,
                    USER_ROLE,
                    userContent,
                    currentTime));
            messageQueue.addLast(message(
                    conversationId,
                    ASSISTANT_ROLE,
                    assistantContent,
                    currentTime));
            while (messageQueue.size() > properties.getRetainedMessages()) {
                messageQueue.removeFirst();
            }
        }
    }

    @Override
    public void clear(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            String safeConversationId = conversationId.strip();
            conversationMap.remove(safeConversationId);
            summaryMap.remove(safeConversationId);
        }
    }

    Instant currentTime() {
        return Instant.now();
    }

    private void removeExpired(
            Deque<ConversationMessage> messageQueue,
            Instant currentTime) {
        if (!properties.expirationEnabled()) {
            return;
        }
        Instant cutoffTime = currentTime.minus(properties.getExpireAfter());
        while (!messageQueue.isEmpty()
                && messageQueue.getFirst().createdAt().isBefore(cutoffTime)) {
            messageQueue.removeFirst();
        }
    }

    private ConversationMessage message(
            String conversationId,
            String role,
            String content,
            Instant currentTime) {
        return new ConversationMessage(
                sequence.incrementAndGet(),
                conversationId,
                role,
                truncate(content),
                currentTime,
                currentTime);
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
        return conversationId.strip();
    }

    private record SummaryEntry(String summary, Instant updatedAt) {
    }
}
