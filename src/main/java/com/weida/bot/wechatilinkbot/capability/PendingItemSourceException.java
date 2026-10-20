package com.weida.bot.wechatilinkbot.capability;

/**
 * Signals that one pending-item source could not be read while other sources may
 * still be returned.
 */
public final class PendingItemSourceException extends RuntimeException {

    private static final int MAX_USER_MESSAGE_CHARACTERS = 240;
    private final String userMessage;

    public PendingItemSourceException(String userMessage) {
        super(userMessage);
        this.userMessage = requireMessage(userMessage);
    }

    public PendingItemSourceException(String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = requireMessage(userMessage);
    }

    public String userMessage() {
        return userMessage;
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
        String normalized = message.strip().replace('\r', ' ').replace('\n', ' ');
        return normalized.length() <= MAX_USER_MESSAGE_CHARACTERS
                ? normalized
                : normalized.substring(0, MAX_USER_MESSAGE_CHARACTERS - 1) + "…";
    }
}
