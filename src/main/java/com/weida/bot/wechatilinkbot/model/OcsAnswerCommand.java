package com.weida.bot.wechatilinkbot.model;

import java.util.List;

/** Bounded semantic answer action returned to the browser companion. */
public record OcsAnswerCommand(
        String sessionId,
        String questionId,
        String action,
        List<String> selectedOptionIds,
        String textAnswer,
        double confidence) {

    public OcsAnswerCommand {
        sessionId = requireText(sessionId, "sessionId");
        questionId = requireText(questionId, "questionId");
        action = requireText(action, "action");
        selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
        textAnswer = textAnswer == null ? "" : textAnswer.strip();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
