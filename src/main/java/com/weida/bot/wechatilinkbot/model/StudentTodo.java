package com.weida.bot.wechatilinkbot.model;

import java.time.Instant;

/**
 * One user-owned student todo item, identified by conversation and persisted independently.
 *
 * <p>This is an immutable record; state transitions (e.g. completion) return new instances.</p>
 */
public record StudentTodo(
        String id,
        String conversationId,
        String title,
        Instant dueTime,
        StudentTodoStatus status,
        String source,
        Instant createdAt) {

    private static final int MAX_TITLE_CHARACTERS = 200;
    private static final int MAX_SOURCE_CHARACTERS = 100;

    public StudentTodo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("待办 ID 不能为空");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("待办会话不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("待办标题不能为空");
        }
        if (title.length() > MAX_TITLE_CHARACTERS) {
            throw new IllegalArgumentException("待办标题不能超过 " + MAX_TITLE_CHARACTERS + " 字符");
        }
        if (dueTime == null) {
            throw new IllegalArgumentException("待办截止时间不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("待办状态不能为空");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("创建时间不能为空");
        }
        id = id.strip();
        conversationId = conversationId.strip();
        title = title.strip();
        source = normalizeSource(source);
    }

    /** Returns a new instance with {@link StudentTodoStatus#COMPLETED}. */
    public StudentTodo complete() {
        if (status != StudentTodoStatus.PENDING) {
            throw new IllegalStateException("只能完成处于待办状态的待办");
        }
        return new StudentTodo(id, conversationId, title, dueTime, StudentTodoStatus.COMPLETED, source, createdAt);
    }

    /** Returns a new instance with {@link StudentTodoStatus#CANCELED}. */
    public StudentTodo cancel() {
        if (status != StudentTodoStatus.PENDING) {
            throw new IllegalStateException("只能取消处于待办状态的待办");
        }
        return new StudentTodo(id, conversationId, title, dueTime, StudentTodoStatus.CANCELED, source, createdAt);
    }

    private static String normalizeSource(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > MAX_SOURCE_CHARACTERS) {
            return null;
        }
        return normalized;
    }
}
