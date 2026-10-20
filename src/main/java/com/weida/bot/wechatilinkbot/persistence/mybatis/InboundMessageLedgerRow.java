package com.weida.bot.wechatilinkbot.persistence.mybatis;

/** Minimal row projection used to translate ledger state without exposing SQL models. */
public class InboundMessageLedgerRow {

    private String status;
    private String leaseId;
    private int attemptCount;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLeaseId() {
        return leaseId;
    }

    public void setLeaseId(String leaseId) {
        this.leaseId = leaseId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }
}
