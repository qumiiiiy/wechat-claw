package com.weida.bot.wechatilinkbot.channel.web;

import java.util.List;

/**
 * Answer in the OCS question bank format.
 */
public record OcsQuestionBankResponse(
        String questionId,
        String answer,
        double confidence,
        String rationale,
        String source) {

    public static OcsQuestionBankResponse fromCandidate(
            String questionId,
            List<String> selectedOptionIds,
            String textAnswer,
            double confidence,
            String rationale,
            String source) {
        String answer;
        if (!selectedOptionIds.isEmpty()) {
            answer = String.join(",", selectedOptionIds);
        } else {
            answer = textAnswer;
        }
        return new OcsQuestionBankResponse(questionId, answer, confidence, rationale, source);
    }
}
