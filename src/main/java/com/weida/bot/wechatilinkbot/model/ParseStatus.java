package com.weida.bot.wechatilinkbot.model;

/**
 * 题目解析状态。
 * 每种失败模式有明确的语义。
 */
public enum ParseStatus {
    OK,
    MISSING_STEM,
    EMPTY_OPTIONS,
    DUPLICATE_OPTIONS,
    NO_STABLE_ID,
    IFRAME_NOT_LOADED,
    UNKNOWN_TYPE,
    EMPTY_HTML,
    PARSE_ERROR
}
