package com.weida.bot.wechatilinkbot.persistence.mybatis;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** MyBatis statements for the iLink inbound processing ledger. */
public interface InboundMessageLedgerMapper {

    void claim(
            @Param("botId") String botId,
            @Param("messageId") long messageId,
            @Param("senderId") String senderId,
            @Param("conversationId") String conversationId,
            @Param("firstReceivedAt") Instant firstReceivedAt,
            @Param("now") Instant now,
            @Param("leaseId") String leaseId,
            @Param("leaseUntil") Instant leaseUntil);

    InboundMessageLedgerRow find(
            @Param("botId") String botId,
            @Param("messageId") long messageId);

    int complete(
            @Param("botId") String botId,
            @Param("messageId") long messageId,
            @Param("leaseId") String leaseId,
            @Param("completedAt") Instant completedAt);

    int fail(
            @Param("botId") String botId,
            @Param("messageId") long messageId,
            @Param("leaseId") String leaseId,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("error") String error,
            @Param("terminal") boolean terminal);

    int purgeBefore(@Param("cutoff") Instant cutoff);
}
