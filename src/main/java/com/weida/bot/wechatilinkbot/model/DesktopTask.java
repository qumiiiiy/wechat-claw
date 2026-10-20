package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;

/**
 * One persisted, user-owned desktop task. V1 supports only opening a validated HTTPS URL.
 */
public record DesktopTask(
        String taskId,
        String conversationId,
        String url,
        DesktopTaskStatus status,
        Instant createdAt,
        Instant approvalExpiresAt,
        Instant approvedAt,
        Instant finishedAt,
        String resultCode,
        String resultSummary) {

    public DesktopTask {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("桌面任务 ID 不能为空");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("桌面任务会话不能为空");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("桌面任务 URL 不能为空");
        }
        if (status == null || createdAt == null || approvalExpiresAt == null) {
            throw new IllegalArgumentException("桌面任务状态和时间不能为空");
        }
        taskId = taskId.strip();
        conversationId = conversationId.strip();
        url = url.strip();
        resultCode = normalizeNullable(resultCode);
        resultSummary = normalizeNullable(resultSummary);
    }

    public boolean isTerminal() {
        return status == DesktopTaskStatus.SUCCEEDED
                || status == DesktopTaskStatus.FAILED
                || status == DesktopTaskStatus.CANCELLED
                || status == DesktopTaskStatus.TIMED_OUT;
    }

    public boolean isAwaitingApproval() {
        return status == DesktopTaskStatus.WAITING_APPROVAL;
    }

    public boolean isApprovalExpired(Instant currentTime) {
        return isAwaitingApproval() && !approvalExpiresAt.isAfter(currentTime);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
