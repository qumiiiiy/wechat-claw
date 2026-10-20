package com.weida.bot.wechatilinkbot.agent;

/**
 * Roles supported by the provider-neutral model protocol.
 */
public enum ModelRole {

    /** Trusted runtime instruction. */
    SYSTEM,

    /** User-authored input. */
    USER,

    /** Model-authored output or tool request. */
    ASSISTANT,

    /** Tool execution result. */
    TOOL
}
