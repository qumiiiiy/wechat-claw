package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 学习通任务数据结构。
 * 包含规范化任务类型、完成状态、可空截止时间和解析元数据。
 */
public record CourseTask(
    String taskId,
    String taskName,
    TaskType taskType,
    CompletionStatus completionStatus,
    Instant deadline,
    String courseId,
    String source,
    String parseNote
) {
    public CourseTask {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(taskName, "taskName must not be null");
        Objects.requireNonNull(taskType, "taskType must not be null");
        Objects.requireNonNull(completionStatus, "completionStatus must not be null");
        Objects.requireNonNull(source, "source must not be null");
        taskId = taskId.strip();
        taskName = taskName.strip();
        courseId = courseId != null ? courseId.strip() : "";
        source = source.strip();
        parseNote = parseNote != null ? parseNote.strip() : "";
    }

    /**
     * 生成稳定去重键：来源 + 课程 ID + 任务 ID。
     */
    public String deduplicationKey() {
        return source + ":" + courseId + ":" + taskId;
    }

    /**
     * 判断任务是否已完成。
     */
    public boolean isCompleted() {
        return completionStatus == CompletionStatus.COMPLETED;
    }

    /**
     * 判断任务是否未完成。
     */
    public boolean isPending() {
        return completionStatus == CompletionStatus.PENDING;
    }

    /**
     * 规范化任务类型。
     */
    public enum TaskType {
        CHAPTER("章节"),
        HOMEWORK("作业"),
        EXAM("考试"),
        UNKNOWN("未知");

        private final String displayName;

        TaskType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        /**
         * 从原始文本规范化任务类型。
         */
        public static TaskType fromRawText(String rawText) {
            if (rawText == null || rawText.isBlank()) {
                return UNKNOWN;
            }
            String normalized = rawText.strip().toLowerCase();
            if (normalized.contains("章") || normalized.contains("节") || normalized.contains("视频")) {
                return CHAPTER;
            }
            if (normalized.contains("作业") || normalized.contains("练习") || normalized.contains("work")) {
                return HOMEWORK;
            }
            if (normalized.contains("考试") || normalized.contains("测试") || normalized.contains("exam")) {
                return EXAM;
            }
            return UNKNOWN;
        }
    }

    /**
     * 规范化完成状态。
     */
    public enum CompletionStatus {
        COMPLETED("已完成"),
        PENDING("未完成"),
        UNKNOWN("未知");

        private final String displayName;

        CompletionStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        /**
         * 从原始文本规范化完成状态。
         */
        public static CompletionStatus fromRawText(String rawText) {
            if (rawText == null || rawText.isBlank()) {
                return UNKNOWN;
            }
            String normalized = rawText.strip();
            // 优先检查 PENDING 状态
            if (normalized.contains("未完成") || normalized.contains("未做")
                    || normalized.contains("未交") || normalized.contains("未提交")
                    || normalized.contains("已过期") || normalized.contains("已截止")
                    || normalized.contains("未通过")
                    || normalized.startsWith("待做") || normalized.startsWith("待提交")
                    || normalized.startsWith("待互评") || normalized.contains("待完成")
                    || normalized.contains("进行中")) {
                return PENDING;
            }
            if (normalized.contains("已完成") || normalized.equals("完成")
                    || normalized.contains("done") || normalized.contains("已交")
                    || normalized.contains("已提交") || normalized.contains("批阅")) {
                return COMPLETED;
            }
            return UNKNOWN;
        }
    }
}
