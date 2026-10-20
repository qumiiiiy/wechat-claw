package com.weida.bot.wechatilinkbot.agent;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * Serializes runs per conversation while bounding global model concurrency.
 */
@Component
public class ConversationCoordinator {

    private final Semaphore runPermits;
    private final Map<String, RetainedLock> conversationLockMap = new ConcurrentHashMap<>();

    public ConversationCoordinator(AgentProperties properties) {
        runPermits = new Semaphore(properties.getMaxConcurrentRuns());
    }

    public <T> T execute(String conversationId, AgentAction<T> action) {
        String safeConversationId = requireConversationId(conversationId);
        RetainedLock retainedLock = retain(safeConversationId);
        retainedLock.lock();
        try {
            return withPermit(action);
        } finally {
            retainedLock.unlock();
            release(safeConversationId, retainedLock);
        }
    }

    private <T> T withPermit(AgentAction<T> action) {
        boolean acquired = false;
        try {
            runPermits.acquire();
            acquired = true;
            return action.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AgentException("等待模型响应时被中断", exception);
        } catch (IOException exception) {
            throw new AgentException("调用模型失败：" + exception.getMessage(), exception);
        } finally {
            if (acquired) {
                runPermits.release();
            }
        }
    }

    private RetainedLock retain(String conversationId) {
        return conversationLockMap.compute(conversationId, (key, current) -> {
            RetainedLock selected = current == null ? new RetainedLock() : current;
            selected.retain();
            return selected;
        });
    }

    private void release(String conversationId, RetainedLock expected) {
        conversationLockMap.computeIfPresent(conversationId, (key, current) -> {
            if (current != expected) {
                return current;
            }
            return current.release() == 0 ? null : current;
        });
    }

    private static String requireConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        return conversationId.strip();
    }

    @FunctionalInterface
    public interface AgentAction<T> {

        T run() throws IOException;
    }

    private static final class RetainedLock {

        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger referenceCount = new AtomicInteger();

        private void retain() {
            referenceCount.incrementAndGet();
        }

        private int release() {
            return referenceCount.decrementAndGet();
        }

        private void lock() {
            lock.lock();
        }

        private void unlock() {
            lock.unlock();
        }
    }
}
