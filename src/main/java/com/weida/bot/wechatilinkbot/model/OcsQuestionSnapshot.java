package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Provider-neutral, bounded snapshot of one course interaction question. */
public record OcsQuestionSnapshot(
        String questionId,
        QuestionType type,
        String stem,
        List<OcsQuestionOption> options,
        String lessonTitle,
        String context,
        Instant capturedAt) {

    private static final int MAX_ID_CHARACTERS = 128;
    private static final int MAX_STEM_CHARACTERS = 2_000;
    private static final int MAX_LESSON_CHARACTERS = 300;
    private static final int MAX_CONTEXT_CHARACTERS = 2_000;
    private static final int MAX_OPTIONS = 20;

    public OcsQuestionSnapshot {
        questionId = requireText(questionId, "questionId", MAX_ID_CHARACTERS);
        Objects.requireNonNull(type, "type must not be null");
        stem = requireText(stem, "stem", MAX_STEM_CHARACTERS);
        options = options == null ? List.of() : List.copyOf(options);
        if (options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("too many question options");
        }
        ensureUniqueOptionIds(options);
        if (type.requiresOptions() && options.isEmpty()) {
            throw new IllegalArgumentException("choice question must contain options");
        }
        lessonTitle = optionalText(lessonTitle, "lessonTitle", MAX_LESSON_CHARACTERS);
        context = optionalText(context, "context", MAX_CONTEXT_CHARACTERS);
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
    }

    public Set<String> optionIdSet() {
        return options.stream()
                .map(OcsQuestionOption::id)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static void ensureUniqueOptionIds(List<OcsQuestionOption> optionList) {
        Set<String> seen = new HashSet<>();
        for (OcsQuestionOption option : optionList) {
            Objects.requireNonNull(option, "question option must not be null");
            String normalized = option.id().toUpperCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                throw new IllegalArgumentException("question option ids must be unique");
            }
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

    public enum QuestionType {
        SINGLE_CHOICE,
        MULTIPLE_CHOICE,
        TRUE_FALSE,
        FILL_BLANK,
        UNKNOWN;

        public boolean requiresOptions() {
            return this == SINGLE_CHOICE || this == MULTIPLE_CHOICE || this == TRUE_FALSE;
        }
    }
}
