package com.weida.bot.wechatilinkbot.infrastructure.learning;

/** Current state of the in-memory Chaoxing QR login challenge. */
public enum ChaoxingLoginStatus {
    NOT_STARTED,
    WAITING_SCAN,
    WAITING_CONFIRMATION,
    AUTHENTICATED,
    EXPIRED,
    REJECTED,
    UNAVAILABLE
}
