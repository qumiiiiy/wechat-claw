package com.weida.bot.wechatilinkbot.agent;

/**
 * Safe business exception raised by agent orchestration.
 */
public class AgentException extends RuntimeException {

    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
