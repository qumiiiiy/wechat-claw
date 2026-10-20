package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.InboundMessageClaim;
import com.weida.bot.wechatilinkbot.model.InboundMessageIdentity;
import java.time.Instant;

/**
 * Durable metadata boundary for inbound message delivery.
 *
 * <p>The ledger deliberately stores only routing identity and processing
 * state. An iLink reply token and the original message payload never enter
 * this boundary.</p>
 */
public interface InboundMessageLedger {

    InboundMessageClaim claim(
            InboundMessageIdentity identity,
            Instant now,
            Instant leaseUntil,
            String leaseId);

    boolean complete(
            InboundMessageIdentity identity,
            String leaseId,
            Instant completedAt);

    boolean fail(
            InboundMessageIdentity identity,
            String leaseId,
            Instant nextAttemptAt,
            String error,
            boolean terminal);

    int purgeBefore(Instant cutoff);
}
