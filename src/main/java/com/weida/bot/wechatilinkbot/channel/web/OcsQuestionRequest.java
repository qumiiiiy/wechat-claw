package com.weida.bot.wechatilinkbot.channel.web;

import com.weida.bot.wechatilinkbot.model.OcsQuestionOption;
import com.weida.bot.wechatilinkbot.model.OcsQuestionSnapshot;
import com.weida.bot.wechatilinkbot.model.OcsQuestionSnapshot.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Untrusted bounded question reported by a learning-platform adapter. */
public record OcsQuestionRequest(
        @NotBlank @Size(max = 128) String questionId,
        @NotBlank @Size(max = 32) String type,
        @NotBlank @Size(max = 2_000) String stem,
        @NotNull @Size(max = 20) List<@Valid OcsQuestionOption> options,
        @Size(max = 300) String lessonTitle,
        @Size(max = 2_000) String context) {

    OcsQuestionSnapshot toSnapshot(Instant capturedAt) {
        QuestionType questionType;
        try {
            questionType = QuestionType.valueOf(type.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            questionType = QuestionType.UNKNOWN;
        }
        return new OcsQuestionSnapshot(
                questionId,
                questionType,
                stem,
                options,
                lessonTitle,
                context,
                capturedAt);
    }
}
