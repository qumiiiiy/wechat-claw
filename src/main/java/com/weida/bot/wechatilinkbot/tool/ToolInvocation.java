package com.weida.bot.wechatilinkbot.tool;

/**
 * Executed tool content plus loop-control metadata.
 *
 * @param content content for the model or direct channel response
 * @param returnDirect whether the agent loop must stop immediately
 * @param succeeded whether the tool completed without a normalized failure
 * @param retryable whether a normalized input failure should be returned to the model for retry
 */
public record ToolInvocation(
        String content,
        boolean returnDirect,
        boolean succeeded,
        boolean retryable) {

    /**
     * Keeps the three-argument constructor for provider-neutral test doubles and callers that
     * do not need retry semantics.
     */
    public ToolInvocation(String content, boolean returnDirect, boolean succeeded) {
        this(content, returnDirect, succeeded, false);
    }
}
