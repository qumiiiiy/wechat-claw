package com.weida.bot.wechatilinkbot.persistence;

/**
 * Stable storage exception that does not expose SQL or credentials.
 */
public class ConversationPersistenceException extends RuntimeException {

    public ConversationPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
