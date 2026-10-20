package com.weida.bot.wechatilinkbot.channel.ilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import com.weida.bot.wechatilinkbot.infrastructure.ratelimit.MessageRateLimiter;
import com.weida.bot.wechatilinkbot.infrastructure.ratelimit.RateLimitProperties;
import com.weida.bot.wechatilinkbot.persistence.InMemoryInboundMessageLedger;
import com.weida.bot.wechatilinkbot.persistence.InboundMessageLedger;
import com.weida.bot.wechatilinkbot.persistence.InboundMessageLedgerProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IlinkMessageDispatcherTest {

    @Test
    void allowsTheSameWechatUserToRunConcurrentlyAcrossBotTenants() throws Exception {
        IlinkMessageHandler messageHandler = mock(IlinkMessageHandler.class);
        ILinkClient firstBot = botClient("bot-one@im.bot");
        ILinkClient secondBot = botClient("bot-two@im.bot");
        ExecutorService executor = boundedExecutor(2);
        ScheduledThreadPoolExecutor retryScheduler = new ScheduledThreadPoolExecutor(1);
        IlinkProperties properties = new IlinkProperties();
        IlinkMessageDispatcher dispatcher =
                new IlinkMessageDispatcher(
                        messageHandler,
                        executor,
                        new IlinkConversationIdentity(),
                        rateLimiter(),
                        properties,
                        new InMemoryInboundMessageLedger(
                                new InboundMessageLedgerProperties()),
                        retryScheduler);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch bothFinished = new CountDownLatch(2);

        doAnswer(invocation -> {
            List<WeixinMessage> batch = invocation.getArgument(1);
            if (batch.getFirst().getMessage_id() == 1L) {
                firstStarted.countDown();
            } else {
                secondStarted.countDown();
            }
            try {
                if (batch.getFirst().getMessage_id() == 1L) {
                    assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
                }
            } finally {
                bothFinished.countDown();
            }
            return null;
        }).when(messageHandler).handleMessages(anyClient(), anyList());

        try {
            dispatcher.dispatch(firstBot, List.of(message(1L, "same-user")));
            dispatcher.dispatch(secondBot, List.of(message(2L, "same-user")));

            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(secondStarted.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
        } finally {
            release.countDown();
            assertThat(bothFinished.await(2, TimeUnit.SECONDS)).isTrue();
            retryScheduler.shutdownNow();
            executor.shutdownNow();
        }
    }

    @Test
    void runsDifferentUsersConcurrentlyButPreservesOrderForTheSameUser()
            throws Exception {
        IlinkMessageHandler messageHandler = mock(IlinkMessageHandler.class);
        ILinkClient client = mock(ILinkClient.class);
        ExecutorService executor = boundedExecutor(4);
        ScheduledThreadPoolExecutor retryScheduler = new ScheduledThreadPoolExecutor(1);
        IlinkProperties properties = new IlinkProperties();
        IlinkMessageDispatcher dispatcher =
                new IlinkMessageDispatcher(
                        messageHandler,
                        executor,
                        new IlinkConversationIdentity(),
                        rateLimiter(),
                        properties,
                        new InMemoryInboundMessageLedger(
                                new InboundMessageLedgerProperties()),
                        retryScheduler);

        CountDownLatch firstAStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstA = new CountDownLatch(1);
        CountDownLatch secondAStarted = new CountDownLatch(1);
        CountDownLatch userBStarted = new CountDownLatch(1);
        CountDownLatch allFinished = new CountDownLatch(3);

        doAnswer(invocation -> {
            List<WeixinMessage> batch = invocation.getArgument(1);
            long messageId = batch.getFirst().getMessage_id();
            try {
                if (messageId == 1L) {
                    firstAStarted.countDown();
                    assertThat(releaseFirstA.await(3, TimeUnit.SECONDS)).isTrue();
                } else if (messageId == 2L) {
                    secondAStarted.countDown();
                } else if (messageId == 3L) {
                    userBStarted.countDown();
                }
            } finally {
                allFinished.countDown();
            }
            return null;
        }).when(messageHandler).handleMessages(eq(client), anyList());

        try {
            dispatcher.dispatch(client, List.of(
                    message(1L, "user-a"),
                    message(2L, "user-a"),
                    message(3L, "user-b")));

            assertThat(firstAStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(userBStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(secondAStarted.await(200, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirstA.countDown();
            assertThat(secondAStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(allFinished.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirstA.countDown();
            retryScheduler.shutdownNow();
            executor.shutdownNow();
        }
    }

    @Test
    void retriesAFailedMessageAndDoesNotRunAnAlreadyCompletedDuplicate()
            throws Exception {
        IlinkMessageHandler messageHandler = mock(IlinkMessageHandler.class);
        ILinkClient client = botClient("bot-one@im.bot");
        ExecutorService executor = boundedExecutor(2);
        ScheduledThreadPoolExecutor retryScheduler = new ScheduledThreadPoolExecutor(1);
        IlinkProperties properties = new IlinkProperties();
        properties.setMessageRetryMaxAttempts(2);
        properties.setMessageRetryInitialDelay(Duration.ofMillis(10));
        properties.setMessageRetryMaxDelay(Duration.ofMillis(10));
        properties.setMessageProcessingLease(Duration.ofMinutes(1));
        InboundMessageLedger ledger = new InMemoryInboundMessageLedger(
                new InboundMessageLedgerProperties());
        IlinkMessageDispatcher dispatcher = new IlinkMessageDispatcher(
                messageHandler,
                executor,
                new IlinkConversationIdentity(),
                rateLimiter(),
                properties,
                ledger,
                retryScheduler);
        AtomicInteger invocationCount = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(1);

        doAnswer(invocation -> {
            if (invocationCount.getAndIncrement() == 0) {
                throw new IllegalStateException("transient failure");
            }
            completed.countDown();
            return null;
        }).when(messageHandler).handleMessages(eq(client), anyList());

        try {
            WeixinMessage message = message(71L, "retry-user");
            dispatcher.dispatch(client, List.of(message));
            dispatcher.dispatch(client, List.of(message));

            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(invocationCount).hasValue(2);
        } finally {
            retryScheduler.shutdownNow();
            executor.shutdownNow();
        }
    }

    private static ExecutorService boundedExecutor(int threadCount) {
        return new ThreadPoolExecutor(
                threadCount,
                threadCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32));
    }

    private static WeixinMessage message(long id, String sender) {
        WeixinMessage message = new WeixinMessage();
        message.setMessage_id(id);
        message.setFrom_user_id(sender);
        return message;
    }

    private static ILinkClient botClient(String botId) {
        ILinkClient client = mock(ILinkClient.class);
        when(client.getLoginContext()).thenReturn(new LoginContext(
                "token",
                "owner",
                botId,
                "https://example.test"));
        return client;
    }

    private static ILinkClient anyClient() {
        return org.mockito.ArgumentMatchers.any(ILinkClient.class);
    }

    private static MessageRateLimiter rateLimiter() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(false);
        return new MessageRateLimiter(properties);
    }
}
