package com.weida.bot.wechatilinkbot.model;

import java.util.Locale;

/**
 * 规范化题目类型。
 */
public enum QuestionType {
    SINGLE_CHOICE("单选"),
    MULTIPLE_CHOICE("多选"),
    TRUE_FALSE("判断"),
    FILL_BLANK("填空"),
    UNKNOWN("未知");

    private final String displayName;

    QuestionType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 从原始 DOM 文本或类名规范化题型。
     */
    public static QuestionType fromRawText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return UNKNOWN;
        }
        String normalized = rawText.strip().toLowerCase(Locale.ROOT);
        if (normalized.contains("单选") || normalized.contains("radio") || normalized.contains("danx")) {
            return SINGLE_CHOICE;
        }
        if (normalized.contains("多选") || normalized.contains("checkbox") || normalized.contains("duox")) {
            return MULTIPLE_CHOICE;
        }
        if (normalized.contains("判断") || normalized.contains("truefalse")
                || normalized.contains("true_false") || normalized.contains("pand")) {
            return TRUE_FALSE;
        }
        if (normalized.contains("填空") || normalized.contains("fill")
                || normalized.contains("blank") || normalized.contains("tiank")) {
            return FILL_BLANK;
        }
        return UNKNOWN;
    }
}
