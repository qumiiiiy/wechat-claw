package com.weida.bot.wechatilinkbot.channel.ilink;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import com.weida.bot.wechatilinkbot.infrastructure.ratelimit.MessageRateLimiter;
import com.weida.bot.wechatilinkbot.infrastructure.ratelimit.RateLimitDecision;
import com.weida.bot.wechatilinkbot.model.InboundMessageClaim;
import com.weida.bot.wechatilinkbot.model.InboundMessageIdentity;
import com.weida.bot.wechatilinkbot.persistence.InboundMessageLedger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Bounded-executor dispatcher with a FIFO task queue for each conversation.
 *
 * <p>Queue admission is separate from message claiming. This keeps a message
 * lease from expiring while it waits behind a slow message, and lets a
 * redelivered duplicate be discarded after the first execution completes.</p>
 */
@Component
public class IlinkMessageDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(IlinkMessageDispatcher.class);
    private final IlinkMessageHandler messageHandler;
    private final ExecutorService executor;
    private final IlinkConversationIdentity conversationIdentity;
    private final MessageRateLimiter rateLimiter;
    private final IlinkProperties properties;
    private final InboundMessageLedger messageLedger;
    private final ScheduledExecutorService retryScheduler;
    private final ConcurrentHashMap<String, UserQueue> userQueueMap =
            new ConcurrentHashMap<>();

    public IlinkMessageDispatcher(
            IlinkMessageHandler messageHandler,
            @Qualifier("wechatMessageHandlingExecutor") ExecutorService executor,
            IlinkConversationIdentity conversationIdentity,
            MessageRateLimiter rateLimiter,
            IlinkProperties properties,
            InboundMessageLedger messageLedger,
            @Qualifier("wechatMessageRetryScheduler") ScheduledExecutorService retryScheduler) {
        this.messageHandler = messageHandler;
        this.executor = executor;
        this.conversationIdentity = conversationIdentity;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.messageLedger = messageLedger;
        this.retryScheduler = retryScheduler;
    }

    public void dispatch(ILinkClient client, List<WeixinMessage> messageList) {
        if (client == null || messageList == null || messageList.isEmpty()) {
            return;
        }
        for (WeixinMessage message : messageList) {
            if (message != null) {
                enqueue(client, message);
            }
        }
    }

    private void enqueue(ILinkClient client, WeixinMessage message) {
        String userKey = conversationIdentity.conversationId(client, message);
        RateLimitDecision decision = rateLimiter.acquire(userKey);
        MessageWork work = new MessageWork(
                client,
                message,
                userKey,
                decision == RateLimitDecision.REJECTED_NOTIFY,
                decision == RateLimitDecision.REJECTED_SILENT,
                1,
                inboundIdentity(client, message));
        AtomicReference<UserQueue> selectedQueue = new AtomicReference<>();
        AtomicReference<MessageWork> firstWork = new AtomicReference<>();
        userQueueMap.compute(userKey, (key, existingQueue) -> {
            UserQueue queue = existingQueue == null
                    ? new UserQueue(key)
                    : existingQueue;
            selectedQueue.set(queue);
            firstWork.set(queue.enqueue(work));
            return queue;
        });
        if (firstWork.get() != null) {
            selectedQueue.get().submit(firstWork.get());
        }
    }

    private InboundMessageIdentity inboundIdentity(
            ILinkClient client,
            WeixinMessage message) {
        Long messageId = message.getMessage_id();
        String senderId = message.getFrom_user_id();
        if (messageId == null || senderId == null || senderId.isBlank()) {
            return null;
        }
        try {
            return new InboundMessageIdentity(
                    conversationIdentity.botId(client),
                    messageId,
                    senderId,
                    conversationIdentity.conversationId(client, message),
                    Instant.now());
        } catch (IllegalArgumentException exception) {
            LOG.warn(
                    "wechat_inbound_ledger_identity_rejected messageRef={} reason={}",
                    IlinkLogReference.of(messageId),
                    exception.getMessage());
            return null;
        }
    }

    private void removeIfIdle(String key, UserQueue expectedQueue) {
        userQueueMap.computeIfPresent(key, (ignored, currentQueue) ->
                currentQueue == expectedQueue && currentQueue.isIdle()
                        ? null
                        : currentQueue);
    }

    private InboundMessageClaim claim(MessageWork work) {
        if (work.identity() == null) {
            return InboundMessageClaim.claimed("untracked", work.attemptNumber());
        }
        String leaseId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try {
            InboundMessageClaim claim = messageLedger.claim(
                    work.identity(),
                    now,
                    now.plus(properties.getMessageProcessingLease()),
                    leaseId);
            if (!claim.claimed()) {
                return claim;
            }
            return InboundMessageClaim.claimed(leaseId, claim.attemptCount());
        } catch (RuntimeException exception) {
            // Processing without a ledger is preferable to silently dropping the inbound payload.
            LOG.warn(
                    "wechat_inbound_ledger_unavailable messageRef={} "
                            + "processing_without_deduplication=true reason={}",
                    IlinkLogReference.of(work.message().getMessage_id()),
                    exception.getMessage());
            return InboundMessageClaim.claimed("untracked", work.attemptNumber());
        }
    }

    private void complete(ClaimedWork work) {
        if (!work.tracked()) {
            return;
        }
        try {
            if (!messageLedger.complete(work.source().identity(), work.leaseId(), Instant.now())) {
                LOG.warn(
                        "wechat_inbound_ledger_lease_lost messageRef={}",
                        IlinkLogReference.of(work.source().message().getMessage_id()));
            }
        } catch (RuntimeException exception) {
            LOG.warn(
                    "wechat_inbound_ledger_complete_failed messageRef={} reason={}",
                    IlinkLogReference.of(work.source().message().getMessage_id()),
                    exception.getMessage());
        }
    }

    private boolean retryOrDeadLetter(ClaimedWork work, RuntimeException failure) {
        int attempt = Math.max(1, work.attemptNumber());
        if (attempt >= properties.getMessageRetryMaxAttempts()) {
            recordFailure(work, failure, null, true);
            LOG.error(
                    "wechat_user_message_dead_lettered userReference={} "
                            + "messageRef={} attempts={}",
                    userReference(work.source().queueKey()),
                    IlinkLogReference.of(work.source().message().getMessage_id()),
                    attempt);
            return false;
        }
        Duration delay = retryDelay(attempt);
        Instant nextAttemptAt = Instant.now().plus(delay);
        recordFailure(work, failure, nextAttemptAt, false);
        MessageWork retryWork = work.source().withAttemptNumber(attempt + 1);
        if (schedule(() -> work.queue().submit(retryWork), delay)) {
            LOG.warn(
                    "wechat_user_message_retry_scheduled userReference={} "
                            + "messageRef={} attempt={} delayMs={}",
                    userReference(work.source().queueKey()),
                    IlinkLogReference.of(work.source().message().getMessage_id()),
                    attempt + 1,
                    delay.toMillis());
            return true;
        }
        recordFailure(work, failure, null, true);
        LOG.error(
                "wechat_user_message_retry_unavailable userReference={} "
                        + "messageRef={} terminal=true",
                userReference(work.source().queueKey()),
                IlinkLogReference.of(work.source().message().getMessage_id()));
        return false;
    }

    private void recordFailure(
            ClaimedWork work,
            RuntimeException failure,
            Instant nextAttemptAt,
            boolean terminal) {
        if (!work.tracked()) {
            return;
        }
        try {
            messageLedger.fail(
                    work.source().identity(),
                    work.leaseId(),
                    nextAttemptAt,
                    failure.getClass().getSimpleName(),
                    terminal);
        } catch (RuntimeException exception) {
            LOG.warn(
                    "wechat_inbound_ledger_failure_record_failed messageRef={} reason={}",
                    IlinkLogReference.of(work.source().message().getMessage_id()),
                    exception.getMessage());
        }
    }

    private Duration retryDelay(int failedAttempt) {
        Duration initial = properties.getMessageRetryInitialDelay();
        Duration maximum = properties.getMessageRetryMaxDelay();
        Duration delay = initial;
        for (int index = 1; index < failedAttempt; index++) {
            if (delay.compareTo(maximum) >= 0) {
                return maximum;
            }
            try {
                delay = delay.multipliedBy(2);
            } catch (ArithmeticException exception) {
                return maximum;
            }
        }
        return delay.compareTo(maximum) > 0 ? maximum : delay;
    }

    private boolean schedule(Runnable task, Duration delay) {
        if (retryScheduler == null || retryScheduler.isShutdown()) {
            return false;
        }
        try {
            retryScheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private final class UserQueue {

        private final String key;
        private final Queue<MessageWork> pendingTaskQueue = new ArrayDeque<>();
        private boolean running;

        private UserQueue(String key) {
            this.key = key;
        }

        private MessageWork enqueue(MessageWork work) {
            synchronized (this) {
                pendingTaskQueue.add(work);
                if (running) {
                    return null;
                }
                running = true;
                return pendingTaskQueue.remove();
            }
        }

        private void submit(MessageWork work) {
            try {
                executor.execute(() -> execute(work));
            } catch (RejectedExecutionException exception) {
                if (schedule(() -> submit(work), properties.getMessageRetryInitialDelay())) {
                    LOG.warn(
                            "wechat_user_message_executor_busy userReference={} retrying=true",
                            userReference(key));
                    return;
                }
                LOG.warn(
                        "wechat_user_message_executor_unavailable userReference={} "
                                + "messageRef={} dropped_before_processing=true",
                        userReference(key),
                        IlinkLogReference.of(work.message().getMessage_id()),
                        exception);
                taskCompleted();
            }
        }

        private void execute(MessageWork work) {
            boolean retryScheduled = false;
            try {
                InboundMessageClaim claim = claim(work);
                if (!claim.claimed()) {
                    return;
                }
                ClaimedWork claimedWork = new ClaimedWork(
                        this,
                        work,
                        claim.leaseId(),
                        Math.max(1, claim.attemptCount()),
                        !"untracked".equals(claim.leaseId()));
                if (work.silentlyDropped()) {
                    complete(claimedWork);
                    return;
                }
                try {
                    if (work.rateLimited()) {
                        messageHandler.handleRateLimitedMessage(work.client(), work.message());
                    } else {
                        messageHandler.handleMessages(work.client(), List.of(work.message()));
                    }
                    complete(claimedWork);
                } catch (RuntimeException exception) {
                    retryScheduled = retryOrDeadLetter(claimedWork, exception);
                }
            } finally {
                if (!retryScheduled) {
                    taskCompleted();
                }
            }
        }

        private void taskCompleted() {
            MessageWork nextTask;
            synchronized (this) {
                nextTask = pendingTaskQueue.poll();
                if (nextTask == null) {
                    running = false;
                }
            }
            if (nextTask != null) {
                submit(nextTask);
            } else {
                removeIfIdle(key, this);
            }
        }

        private synchronized boolean isIdle() {
            return !running && pendingTaskQueue.isEmpty();
        }
    }

    private record MessageWork(
            ILinkClient client,
            WeixinMessage message,
            String queueKey,
            boolean rateLimited,
            boolean silentlyDropped,
            int attemptNumber,
            InboundMessageIdentity identity) {

        private MessageWork withAttemptNumber(int nextAttemptNumber) {
            return new MessageWork(
                    client,
                    message,
                    queueKey,
                    rateLimited,
                    silentlyDropped,
                    nextAttemptNumber,
                    identity);
        }
    }

    private record ClaimedWork(
            UserQueue queue,
            MessageWork source,
            String leaseId,
            int attemptNumber,
            boolean tracked) {
    }

    private static String userReference(String userKey) {
        return Integer.toUnsignedString(userKey.hashCode(), 16);
    }
}
