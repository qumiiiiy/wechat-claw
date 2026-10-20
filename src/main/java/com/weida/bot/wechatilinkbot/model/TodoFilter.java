package com.weida.bot.wechatilinkbot.model;

/** Dimension for querying student todos. */
public enum TodoFilter {
    ALL,
    TODAY,
    TOMORROW,
    WEEK,
    NEXT_WEEK,
    MONTH,
    OVERDUE,
    EMAIL_ONLY,
    COURSE_ONLY
}
