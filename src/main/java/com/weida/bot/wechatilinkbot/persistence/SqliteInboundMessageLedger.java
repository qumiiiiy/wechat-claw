package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.InboundMessageClaim;
import com.weida.bot.wechatilinkbot.model.InboundMessageIdentity;
import com.weida.bot.wechatilinkbot.persistence.mybatis.InboundMessageLedgerMapper;
import com.weida.bot.wechatilinkbot.persistence.mybatis.InboundMessageLedgerRow;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/** Transactional SQLite implementation of the inbound message ledger. */
public class SqliteInboundMessageLedger implements InboundMessageLedger {

    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";
    private static final String DEAD = "DEAD";

    private final InboundMessageLedgerMapper mapper;
    private final InboundMessageLedgerProperties properties;

    public SqliteInboundMessageLedger(
            InboundMessageLedgerMapper mapper,
            InboundMessageLedgerProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public InboundMessageClaim claim(
            InboundMessageIdentity identity,
            Instant now,
            Instant leaseUntil,
            String leaseId) {
        validateClaimArguments(identity, now, leaseUntil, leaseId);
        try {
            mapper.purgeBefore(now.minus(properties.getDeduplicationTtl()));
            mapper.claim(
                    identity.botId(),
                    identity.messageId(),
                    identity.senderId(),
                    identity.conversationId(),
                    identity.receivedAt(),
                    now,
                    leaseId,
                    leaseUntil);
            InboundMessageLedgerRow row = mapper.find(identity.botId(), identity.messageId());
            return toClaim(row, leaseId);
        } catch (DataAccessException exception) {
            throw storageFailure("领取", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public boolean complete(
            InboundMessageIdentity identity,
            String leaseId,
            Instant completedAt) {
        try {
            return mapper.complete(
                    identity.botId(), identity.messageId(), leaseId, completedAt) > 0;
        } catch (DataAccessException exception) {
            throw storageFailure("完成", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public boolean fail(
            InboundMessageIdentity identity,
            String leaseId,
            Instant nextAttemptAt,
            String error,
            boolean terminal) {
        try {
            return mapper.fail(
                    identity.botId(),
                    identity.messageId(),
                    leaseId,
                    nextAttemptAt,
                    truncate(error),
                    terminal) > 0;
        } catch (DataAccessException exception) {
            throw storageFailure("失败记录", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public int purgeBefore(Instant cutoff) {
        try {
            return mapper.purgeBefore(cutoff);
        } catch (DataAccessException exception) {
            throw storageFailure("清理", exception);
        }
    }

    private InboundMessageClaim toClaim(InboundMessageLedgerRow row, String leaseId) {
        if (row == null) {
            throw new InboundMessageLedgerException(
                    "SQLite 入站消息领取后未找到状态", null);
        }
        int attempts = row.getAttemptCount();
        if (leaseId.equals(row.getLeaseId()) && PROCESSING.equals(row.getStatus())) {
            return InboundMessageClaim.claimed(leaseId, attempts);
        }
        if (COMPLETED.equals(row.getStatus())) {
            return InboundMessageClaim.completed(attempts);
        }
        if (DEAD.equals(row.getStatus())) {
            return InboundMessageClaim.dead(attempts);
        }
        return InboundMessageClaim.busy(attempts);
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

    private String truncate(String value) {
        String safeValue = value == null ? "" : value;
        int maxCharacters = properties.getMaxErrorCharacters();
        return safeValue.length() <= maxCharacters
                ? safeValue
                : safeValue.substring(0, maxCharacters) + "…";
    }

    private static InboundMessageLedgerException storageFailure(
            String operation,
            DataAccessException exception) {
        return new InboundMessageLedgerException("SQLite 入站消息账本" + operation + "失败", exception);
    }
}
