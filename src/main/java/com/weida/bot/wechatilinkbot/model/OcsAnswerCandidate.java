package com.weida.bot.wechatilinkbot.model;

import java.util.LinkedHashSet;
import java.util.List;

/** Validated answer proposed by the configured online Agent model. */
public record OcsAnswerCandidate(
        String questionId,
        List<String> selectedOptionIds,
        String textAnswer,
        String rationale,
        double confidence,
        String source) {

    private static final int MAX_ANSWER_CHARACTERS = 1_000;
    private static final int MAX_RATIONALE_CHARACTERS = 1_000;

    public OcsAnswerCandidate {
        questionId = requireText(questionId, "questionId", 128);
        selectedOptionIds = selectedOptionIds == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(selectedOptionIds.stream()
                        .map(value -> requireText(value, "selected option id", 16))
                        .toList()));
        textAnswer = optionalText(textAnswer, "textAnswer", MAX_ANSWER_CHARACTERS);
        rationale = optionalText(rationale, "rationale", MAX_RATIONALE_CHARACTERS);
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        source = requireText(source, "source", 64);
        if (selectedOptionIds.isEmpty() && textAnswer.isBlank()) {
            throw new IllegalArgumentException("answer must contain options or text");
        }
    }

    private static String requireText(String value, String field, int maxCharacters) {
        String safeValue = optionalText(value, field, maxCharacters);
        if (safeValue.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return safeValue;
    }

    private static String optionalText(String value, String field, int maxCharacters) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String safeValue = value.strip();
        if (safeValue.length() > maxCharacters) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return safeValue;
    }
}
