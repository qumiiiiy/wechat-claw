package com.weida.bot.wechatilinkbot.infrastructure.ratelimit;

/**
 * Result of one inbound message rate-limit check.
 *
 * @author ryx
 * @since 2026-07-21
 */
public enum RateLimitDecision {

    /** Process the message normally. */
    ALLOWED,

    /** Reject the message and enqueue one user-visible notice for the current window. */
    REJECTED_NOTIFY,

    /** Reject the message silently because this window already produced a notice. */
    REJECTED_SILENT
}
