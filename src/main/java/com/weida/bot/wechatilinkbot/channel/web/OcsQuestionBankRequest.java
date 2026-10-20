package com.weida.bot.wechatilinkbot.channel.web;

import com.weida.bot.wechatilinkbot.model.OcsQuestionOption;
import com.weida.bot.wechatilinkbot.model.OcsQuestionSnapshot;
import com.weida.bot.wechatilinkbot.model.OcsQuestionSnapshot.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Question in the OCS question bank format, converted to OcsQuestionSnapshot for answering.
 */
public record OcsQuestionBankRequest(
        @NotBlank @Size(max = 128) String questionId,
        @NotBlank @Size(max = 32) String type,
        @NotBlank @Size(max = 2_000) String stem,
        @Size(max = 20) List<Option> options,
        @Size(max = 300) String lessonTitle,
        @Size(max = 2_000) String context) {

    public record Option(
            @NotBlank @Size(max = 16) String label,
            @Size(max = 500) String content) {
    }

    public OcsQuestionSnapshot toSnapshot(Instant capturedAt) {
        QuestionType questionType = parseType(type);
        List<OcsQuestionOption> snapshotOptions = options == null
                ? List.of()
                : options.stream()
                        .map(opt -> new OcsQuestionOption(opt.label(), opt.content()))
                        .toList();
        return new OcsQuestionSnapshot(
                questionId,
                questionType,
                stem,
                snapshotOptions,
                lessonTitle,
                context,
                capturedAt);
    }

    private static QuestionType parseType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return QuestionType.UNKNOWN;
        }
        return switch (rawType.strip().toLowerCase()) {
            case "single", "single_choice", "singlechoice" -> QuestionType.SINGLE_CHOICE;
            case "multiple", "multiple_choice", "multiplechoice" -> QuestionType.MULTIPLE_CHOICE;
            case "judge", "true_false", "truefalse" -> QuestionType.TRUE_FALSE;
            case "fill", "fill_blank", "fillblank" -> QuestionType.FILL_BLANK;
            default -> QuestionType.UNKNOWN;
        };
    }
}
