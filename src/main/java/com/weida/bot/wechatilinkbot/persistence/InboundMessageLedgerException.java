package com.weida.bot.wechatilinkbot.persistence;

/** Stable failure type for inbound ledger storage operations. */
public class InboundMessageLedgerException extends RuntimeException {

    public InboundMessageLedgerException(String message, Throwable cause) {
        super(message, cause);
    }
}
