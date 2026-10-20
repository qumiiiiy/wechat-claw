package com.weida.bot.wechatilinkbot.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConversationCoordinatorTest {

    @Test
    void serializesTheSameConversation() throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.setMaxConcurrentRuns(2);
        ConversationCoordinator coordinator = new ConversationCoordinator(properties);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        runConcurrently(coordinator, List.of("same", "same"), active, maximum);

        assertThat(maximum).hasValue(1);
    }

    @Test
    void permitsDifferentConversationsToRunInParallel() throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.setMaxConcurrentRuns(2);
        ConversationCoordinator coordinator = new ConversationCoordinator(properties);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        runConcurrently(coordinator, List.of("first", "second"), active, maximum);

        assertThat(maximum).hasValue(2);
    }

    private static void runConcurrently(
            ConversationCoordinator coordinator,
            List<String> conversationIds,
            AtomicInteger active,
            AtomicInteger maximum) throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Void>> futures = new ArrayList<>();
            for (String conversationId : conversationIds) {
                futures.add(executor.submit(() -> coordinator.execute(conversationId, () -> {
                    int current = active.incrementAndGet();
                    maximum.accumulateAndGet(current, Math::max);
                    started.countDown();
                    try {
                        release.await(200, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException("test interrupted", exception);
                    }
                    active.decrementAndGet();
                    return null;
                })));
            }
            started.await(200, TimeUnit.MILLISECONDS);
            release.countDown();
            for (Future<Void> future : futures) {
                future.get(2, TimeUnit.SECONDS);
            }
        }
    }
}
