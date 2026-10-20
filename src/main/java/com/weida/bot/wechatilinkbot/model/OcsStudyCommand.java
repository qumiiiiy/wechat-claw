package com.weida.bot.wechatilinkbot.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * One server-created command claimed by the local OCS companion script.
 */
public record OcsStudyCommand(
        String commandId,
        String sessionId,
        String action,
        String courseName,
        String courseReference,
        URI courseUrl,
        URI targetCourseUrl,
        String leaseId,
        Instant leaseExpiresAt,
        Instant createdAt) {

    public OcsStudyCommand {
        commandId = requireText(commandId, "commandId");
        sessionId = requireText(sessionId, "sessionId");
        action = requireText(action, "action");
        courseName = requireText(courseName, "courseName");
        courseReference = safeText(courseReference);
        Objects.requireNonNull(courseUrl, "courseUrl must not be null");
        leaseId = requireText(leaseId, "leaseId");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
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
}
