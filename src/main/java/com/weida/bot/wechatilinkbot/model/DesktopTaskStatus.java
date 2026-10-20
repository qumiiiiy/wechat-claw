package com.weida.bot.wechatilinkbot.model;

/** Lifecycle states for one user-approved desktop task. */
public enum DesktopTaskStatus {
    WAITING_APPROVAL,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}
