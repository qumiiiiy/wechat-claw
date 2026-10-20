package com.weida.bot.wechatilinkbot.model;

/**
 * Result of attempting to claim an inbound message for processing.
 */
public record InboundMessageClaim(Status status, String leaseId, int attemptCount) {

    public enum Status {
        CLAIMED,
        COMPLETED,
        BUSY,
        DEAD
    }

    public InboundMessageClaim {
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount 不能为负数");
        }
        if (status == Status.CLAIMED && (leaseId == null || leaseId.isBlank())) {
            throw new IllegalArgumentException("已领取消息必须有 leaseId");
        }
        if (status != Status.CLAIMED && leaseId != null && !leaseId.isBlank()) {
            leaseId = leaseId.strip();
        }
    }

    public boolean claimed() {
        return status == Status.CLAIMED;
    }

    public static InboundMessageClaim claimed(String leaseId, int attemptCount) {
        return new InboundMessageClaim(Status.CLAIMED, leaseId, attemptCount);
    }

    public static InboundMessageClaim completed(int attemptCount) {
        return new InboundMessageClaim(Status.COMPLETED, null, attemptCount);
    }

    public static InboundMessageClaim busy(int attemptCount) {
        return new InboundMessageClaim(Status.BUSY, null, attemptCount);
    }

    public static InboundMessageClaim dead(int attemptCount) {
        return new InboundMessageClaim(Status.DEAD, null, attemptCount);
    }
}
