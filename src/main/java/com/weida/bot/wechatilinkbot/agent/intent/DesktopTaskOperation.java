package com.weida.bot.wechatilinkbot.agent.intent;

/** Explicit, high-risk desktop operation derived from the current user message. */
public enum DesktopTaskOperation {
    NONE,
    REQUEST_OPEN_URL,
    CONFIRM,
    STATUS,
    CANCEL
}
