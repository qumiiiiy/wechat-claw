package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Provider-neutral snapshot of one bounded OCS-assisted study session.
 */
public record OcsStudySession(
        String sessionId,
        String courseName,
        String chapterName,
        Status status,
        int progressPercent,
        String currentLesson,
        String notes,
        String lastEvent,
        OcsPluginStatus pluginStatus,
        Instant createdAt,
        Instant updatedAt) {

    public OcsStudySession {
        sessionId = requireText(sessionId, "sessionId");
        courseName = requireText(courseName, "courseName");
        chapterName = safeText(chapterName);
        Objects.requireNonNull(status, "status must not be null");
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("progressPercent must be between 0 and 100");
        }
        currentLesson = safeText(currentLesson);
        notes = safeText(notes);
        lastEvent = safeText(lastEvent);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    private static String safeText(String value) {
        return value == null ? "" : value.strip();
    }

    public enum Status {
        CREATED,
        LAUNCHING,
        WAITING_LOGIN,
        DISCOVERING,
        RUNNING,
        WAITING_QUESTION,
        ANSWERING,
        PAUSED,
        PLUGIN_MISSING,
        SUCCEEDED,
        FAILED
    }
}
