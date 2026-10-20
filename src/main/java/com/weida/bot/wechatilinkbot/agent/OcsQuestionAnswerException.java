package com.weida.bot.wechatilinkbot.agent;

/** Stable, non-sensitive failure from the OCS online answer boundary. */
public class OcsQuestionAnswerException extends RuntimeException {

    private final Reason reason;

    public OcsQuestionAnswerException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public OcsQuestionAnswerException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        UNAVAILABLE,
        UNSUPPORTED,
        INVALID_RESPONSE,
        LOW_CONFIDENCE,
        TIMEOUT,
        OVERLOADED,
        PROVIDER_FAILURE
    }
}
