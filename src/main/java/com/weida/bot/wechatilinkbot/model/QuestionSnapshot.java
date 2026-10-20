package com.weida.bot.wechatilinkbot.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 稳定题目快照。
 * 包含指纹、课程/章节引用、规范化题型、有界题干和选项、解析状态。
 * 用于跨模块传递发现的题目，交给 AI 答题编排。
 */
public record QuestionSnapshot(
        String questionId,
        String fingerprint,
        String courseId,
        String chapterId,
        QuestionType questionType,
        String stem,
        List<QuestionOption> options,
        boolean allowMultiple,
        String pageSource,
        Instant capturedAt,
        ParseStatus parseStatus) {

    private static final int MAX_STEM_LENGTH = 5000;
    private static final int MAX_OPTIONS = 20;
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_SOURCE_LENGTH = 2048;
    private static final int FINGERPRINT_HEX_LENGTH = 16;

    public QuestionSnapshot {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(stem, "stem must not be null");
        Objects.requireNonNull(options, "options must not be null");
        Objects.requireNonNull(pageSource, "pageSource must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        Objects.requireNonNull(parseStatus, "parseStatus must not be null");

        questionId = safeStrip(questionId, MAX_ID_LENGTH);
        fingerprint = safeStrip(fingerprint, MAX_ID_LENGTH);
        courseId = safeStrip(courseId, MAX_ID_LENGTH);
        chapterId = safeStrip(chapterId, MAX_ID_LENGTH);
        stem = safeStrip(stem, MAX_STEM_LENGTH);
        pageSource = safeStrip(pageSource, MAX_SOURCE_LENGTH);

        if (options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("options exceeds maximum of " + MAX_OPTIONS);
        }
        options = List.copyOf(options);
    }

    /**
     * 生成稳定指纹：SHA-256(normalizedStem + sortedNormalizedOptions) 取前 16 位 hex。
     */
    public static String computeFingerprint(String stem, List<QuestionOption> options) {
        String normalizedStem = normalizeText(stem);
        List<String> normalizedOptions = new ArrayList<>();
        if (options != null) {
            for (QuestionOption opt : options) {
                normalizedOptions.add(normalizeText(opt.text()));
            }
        }
        Collections.sort(normalizedOptions);

        StringBuilder input = new StringBuilder(normalizedStem);
        for (String optText : normalizedOptions) {
            input.append('|').append(optText);
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.toString().getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash);
            return "sha256:" + hex.substring(0, FINGERPRINT_HEX_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    /**
     * 规范化文本用于指纹计算：去空白、压缩空格、转小写。
     */
    public static String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.strip()
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }

    private static String safeStrip(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            return stripped.substring(0, maxLength);
        }
        return stripped;
    }
}
