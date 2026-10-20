package com.weida.bot.wechatilinkbot.model;

import java.util.Objects;

/**
 * 题目选项。
 * label 为选项标签（A/B/C/D），text 为选项文本。
 */
public record QuestionOption(
        String label,
        String text) {

    private static final int MAX_LABEL_LENGTH = 10;
    private static final int MAX_TEXT_LENGTH = 2000;

    public QuestionOption {
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(text, "text must not be null");
        label = label.strip();
        text = text.strip();
        if (label.isEmpty()) {
            throw new IllegalArgumentException("label must not be empty");
        }
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("label exceeds maximum length of " + MAX_LABEL_LENGTH);
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("text exceeds maximum length of " + MAX_TEXT_LENGTH);
        }
    }
}
