package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.InboundMessageClaim;
import com.weida.bot.wechatilinkbot.model.InboundMessageIdentity;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Non-durable bounded-state ledger used when the persistence adapter is unavailable. */
public class InMemoryInboundMessageLedger implements InboundMessageLedger {

    private final InboundMessageLedgerProperties properties;
    private final Map<Key, Entry> entryMap = new HashMap<>();

    public InMemoryInboundMessageLedger(InboundMessageLedgerProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized InboundMessageClaim claim(
            InboundMessageIdentity identity,
            Instant now,
            Instant leaseUntil,
            String leaseId) {
        validateClaimArguments(identity, now, leaseUntil, leaseId);
        purgeBefore(now.minus(properties.getDeduplicationTtl()));
        Key key = new Key(identity.botId(), identity.messageId());
        Entry entry = entryMap.get(key);
        if (entry == null) {
            entry = new Entry(now);
            entryMap.put(key, entry);
        }
        if (entry.status == Status.COMPLETED) {
            return InboundMessageClaim.completed(entry.attemptCount);
        }
        if (entry.status == Status.DEAD) {
            return InboundMessageClaim.dead(entry.attemptCount);
        }
        if (isBefore(now, entry.leaseUntil) || isBefore(now, entry.nextAttemptAt)) {
            return InboundMessageClaim.busy(entry.attemptCount);
        }
        entry.status = Status.PROCESSING;
        entry.attemptCount++;
        entry.lastReceivedAt = now;
        entry.leaseId = leaseId;
        entry.leaseUntil = leaseUntil;
        entry.nextAttemptAt = null;
        return InboundMessageClaim.claimed(leaseId, entry.attemptCount);
    }

    @Override
    public synchronized boolean complete(
            InboundMessageIdentity identity,
            String leaseId,
            Instant completedAt) {
        Entry entry = entryMap.get(new Key(identity.botId(), identity.messageId()));
        if (!owns(entry, leaseId) || completedAt == null) {
            return false;
        }
        entry.status = Status.COMPLETED;
        entry.lastReceivedAt = completedAt;
        entry.leaseId = null;
        entry.leaseUntil = null;
        entry.nextAttemptAt = null;
        return true;
    }

    @Override
    public synchronized boolean fail(
            InboundMessageIdentity identity,
            String leaseId,
            Instant nextAttemptAt,
            String error,
            boolean terminal) {
        Entry entry = entryMap.get(new Key(identity.botId(), identity.messageId()));
        if (!owns(entry, leaseId)) {
            return false;
        }
        entry.status = terminal ? Status.DEAD : Status.FAILED;
        entry.nextAttemptAt = terminal ? null : nextAttemptAt;
        entry.leaseId = null;
        entry.leaseUntil = null;
        return true;
    }

    @Override
    public synchronized int purgeBefore(Instant cutoff) {
        if (cutoff == null) {
            return 0;
        }
        int removed = 0;
        Iterator<Map.Entry<Key, Entry>> iterator = entryMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.status != Status.PROCESSING
                    && entry.lastReceivedAt.isBefore(cutoff)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    private void validateClaimArguments(
            InboundMessageIdentity identity,
            Instant now,
            Instant leaseUntil,
            String leaseId) {
        if (identity == null || now == null || leaseUntil == null
                || leaseId == null || leaseId.isBlank()) {
            throw new IllegalArgumentException("入站消息领取参数不完整");
        }
        if (!leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("消息租约必须晚于当前时间");
        }
    }

    private boolean owns(Entry entry, String leaseId) {
        return entry != null
                && entry.status == Status.PROCESSING
                && leaseId != null
                && leaseId.equals(entry.leaseId);
    }

    private static boolean isBefore(Instant now, Instant deadline) {
        return deadline != null && now.isBefore(deadline);
    }

    private enum Status {
        PROCESSING,
        FAILED,
        COMPLETED,
        DEAD
    }

    private record Key(String botId, long messageId) {
    }

    private static final class Entry {

        private Instant lastReceivedAt;
        private Status status = Status.PROCESSING;
        private int attemptCount;
        private String leaseId;
        private Instant leaseUntil;
        private Instant nextAttemptAt;

        private Entry(Instant lastReceivedAt) {
            this.lastReceivedAt = lastReceivedAt;
        }
    }
}
