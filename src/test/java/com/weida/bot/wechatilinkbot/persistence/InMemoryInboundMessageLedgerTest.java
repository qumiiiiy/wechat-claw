package com.weida.bot.wechatilinkbot.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.InboundMessageClaim;
import com.weida.bot.wechatilinkbot.model.InboundMessageIdentity;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryInboundMessageLedgerTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-05T01:00:00Z");
    private static final Instant FIRST_NOW = Instant.parse("2026-08-05T01:00:01Z");
    private final InboundMessageLedgerProperties properties = new InboundMessageLedgerProperties();
    private InMemoryInboundMessageLedger ledger;

    @BeforeEach
    void setUp() {
        properties.setDeduplicationTtl(Duration.ofHours(1));
        ledger = new InMemoryInboundMessageLedger(properties);
    }

    @Test
    void suppressesDuplicateWhileProcessingAndAfterCompletion() {
        InboundMessageIdentity identity = identity("bot-a", 41L);

        InboundMessageClaim first = ledger.claim(
                identity,
                FIRST_NOW,
                FIRST_NOW.plusSeconds(30),
                "lease-1");
        InboundMessageClaim duplicateWhileBusy = ledger.claim(
                identity,
                FIRST_NOW.plusSeconds(1),
                FIRST_NOW.plusSeconds(31),
                "lease-2");

        assertThat(first.status()).isEqualTo(InboundMessageClaim.Status.CLAIMED);
        assertThat(first.attemptCount()).isEqualTo(1);
        assertThat(duplicateWhileBusy.status()).isEqualTo(InboundMessageClaim.Status.BUSY);
        assertThat(ledger.complete(identity, "wrong-lease", FIRST_NOW.plusSeconds(2))).isFalse();
        assertThat(ledger.complete(identity, "lease-1", FIRST_NOW.plusSeconds(2))).isTrue();

        assertThat(ledger.claim(
                identity,
                FIRST_NOW.plusSeconds(3),
                FIRST_NOW.plusSeconds(33),
                "lease-3").status())
                .isEqualTo(InboundMessageClaim.Status.COMPLETED);
    }

    @Test
    void reclaimsAfterBackoffAndStopsAtTerminalFailure() {
        InboundMessageIdentity identity = identity("bot-a", 42L);

        ledger.claim(identity, FIRST_NOW, FIRST_NOW.plusSeconds(30), "lease-1");
        Instant nextAttempt = FIRST_NOW.plusSeconds(10);
        assertThat(ledger.fail(
                identity,
                "lease-1",
                nextAttempt,
                "IllegalStateException with user data",
                false)).isTrue();
        assertThat(ledger.claim(
                identity,
                FIRST_NOW.plusSeconds(9),
                FIRST_NOW.plusSeconds(39),
                "lease-2").status())
                .isEqualTo(InboundMessageClaim.Status.BUSY);

        InboundMessageClaim retry = ledger.claim(
                identity,
                nextAttempt,
                nextAttempt.plusSeconds(30),
                "lease-2");
        assertThat(retry.status()).isEqualTo(InboundMessageClaim.Status.CLAIMED);
        assertThat(retry.attemptCount()).isEqualTo(2);
        assertThat(ledger.fail(identity, "lease-2", null, "terminal", true)).isTrue();
        assertThat(ledger.claim(
                identity,
                nextAttempt.plusSeconds(1),
                nextAttempt.plusSeconds(31),
                "lease-3").status())
                .isEqualTo(InboundMessageClaim.Status.DEAD);
    }

    @Test
    void scopesMessageIdsByBotAndPurgesExpiredTerminalState() {
        InboundMessageIdentity firstBot = identity("bot-a", 99L);
        InboundMessageIdentity secondBot = identity("bot-b", 99L);
        ledger.claim(firstBot, FIRST_NOW, FIRST_NOW.plusSeconds(30), "lease-a");
        ledger.complete(firstBot, "lease-a", FIRST_NOW);

        assertThat(ledger.claim(
                secondBot,
                FIRST_NOW,
                FIRST_NOW.plusSeconds(30),
                "lease-b").status())
                .isEqualTo(InboundMessageClaim.Status.CLAIMED);

        int purged = ledger.purgeBefore(FIRST_NOW.plus(properties.getDeduplicationTtl()));
        assertThat(purged).isEqualTo(1);
        assertThat(ledger.claim(
                firstBot,
                FIRST_NOW.plus(properties.getDeduplicationTtl()).plusSeconds(1),
                FIRST_NOW.plus(properties.getDeduplicationTtl()).plusSeconds(31),
                "lease-c").status())
                .isEqualTo(InboundMessageClaim.Status.CLAIMED);
    }

    private static InboundMessageIdentity identity(String botId, long messageId) {
        return new InboundMessageIdentity(
                botId,
                messageId,
                "sender@im.wechat",
                "wechat-ilink|sender@im.wechat",
                RECEIVED_AT);
    }
}
