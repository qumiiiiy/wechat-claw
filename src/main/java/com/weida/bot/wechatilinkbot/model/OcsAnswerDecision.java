package com.weida.bot.wechatilinkbot.model;

import java.util.List;

/** Authorized semantic decision derived from a validated online answer candidate. */
public record OcsAnswerDecision(
        String questionId,
        String action,
        List<String> selectedOptionIds,
        String textAnswer,
        double confidence,
        String modelIdentifier) {

    public OcsAnswerDecision {
        if (questionId == null || questionId.isBlank() || questionId.length() > 128) {
            throw new IllegalArgumentException("questionId must be bounded");
        }
        questionId = questionId.strip();
        if (!"SELECT_OPTIONS".equals(action) && !"FILL_TEXT".equals(action)) {
            throw new IllegalArgumentException("unsupported semantic answer action");
        }
        selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
        textAnswer = textAnswer == null ? "" : textAnswer.strip();
        if (selectedOptionIds.size() > 20 || textAnswer.length() > 1_000) {
            throw new IllegalArgumentException("semantic answer payload is too large");
        }
        if (("SELECT_OPTIONS".equals(action)
                        && (selectedOptionIds.isEmpty() || !textAnswer.isBlank()))
                || ("FILL_TEXT".equals(action)
                        && (!selectedOptionIds.isEmpty() || textAnswer.isBlank()))) {
            throw new IllegalArgumentException("semantic answer action has no payload");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        if (modelIdentifier == null || modelIdentifier.isBlank() || modelIdentifier.length() > 64) {
            throw new IllegalArgumentException("modelIdentifier must be bounded");
        }
        modelIdentifier = modelIdentifier.strip();
    }
}
