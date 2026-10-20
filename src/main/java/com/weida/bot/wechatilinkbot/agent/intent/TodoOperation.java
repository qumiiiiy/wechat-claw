package com.weida.bot.wechatilinkbot.agent.intent;

/** Explicit todo-related operations detected from user text. */
public enum TodoOperation {
    LIST,
    TODAY,
    ADD,
    COMPLETE,
    CANCEL,
    QUERY,
    SUMMARY,
    POSTPONE,
    MULTIPLE,
    NONE
}
