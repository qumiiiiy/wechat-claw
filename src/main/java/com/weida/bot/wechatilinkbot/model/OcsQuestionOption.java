package com.weida.bot.wechatilinkbot.model;

/** One bounded answer option captured by a learning-platform adapter. */
public record OcsQuestionOption(String id, String text) {

    private static final int MAX_ID_CHARACTERS = 16;
    private static final int MAX_TEXT_CHARACTERS = 500;

    public OcsQuestionOption {
        id = requireText(id, "option id", MAX_ID_CHARACTERS);
        text = requireText(text, "option text", MAX_TEXT_CHARACTERS);
    }

    private static String requireText(String value, String field, int maxCharacters) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String safeValue = value.strip();
        if (safeValue.length() > maxCharacters) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return safeValue;
    }
}
