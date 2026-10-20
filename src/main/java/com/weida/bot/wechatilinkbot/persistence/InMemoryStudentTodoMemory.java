package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe bounded in-memory student todo storage.
 *
 * <p>This implementation parallels {@code InMemoryConversationMemory} — it is
 * self-contained, requires no Spring wiring, and stores data in a two-level
 * {@link ConcurrentHashMap} keyed by conversation and todo ID.</p>
 */
public class InMemoryStudentTodoMemory implements StudentTodoMemory {

    private final ConcurrentHashMap<
            String, ConcurrentHashMap<String, StudentTodo>> todosMap = new ConcurrentHashMap<>();

    @Override
    public StudentTodo add(StudentTodo todo) {
        requireNonNull(todo, "待办不能为空");
        requireValidNew(todo);
        ConcurrentHashMap<String, StudentTodo> innerMap =
                todosMap.computeIfAbsent(todo.conversationId(), k -> new ConcurrentHashMap<>());
        StudentTodo previous = innerMap.putIfAbsent(todo.id(), todo);
        if (previous != null) {
            throw new StudentTodoPersistenceException("待办 " + todo.id() + " 已存在");
        }
        return todo;
    }

    @Override
    public List<StudentTodo> listPending(String conversationId) {
        String safeConversationId = requireConversationId(conversationId);
        var innerMap = todosMap.get(safeConversationId);
        if (innerMap == null) {
            return List.of();
        }
        return innerMap.values().stream()
                .filter(todo -> todo.status() == StudentTodoStatus.PENDING)
                .sorted(Comparator.comparing(StudentTodo::dueTime))
                .toList();
    }

    @Override
    public StudentTodo complete(String id, String conversationId) {
        String safeId = requireId(id);
        String safeConversationId = requireConversationId(conversationId);
        var innerMap = todosMap.get(safeConversationId);
        if (innerMap == null) {
            throw new StudentTodoPersistenceException("待办 " + safeId + " 不存在");
        }
        StudentTodo existing = innerMap.get(safeId);
        if (existing == null) {
            throw new StudentTodoPersistenceException("待办 " + safeId + " 不存在");
        }
        if (existing.status() != StudentTodoStatus.PENDING) {
            throw new StudentTodoPersistenceException("待办 " + safeId + " 已完成，不能重复完成");
        }
        StudentTodo completed = existing.complete();
        innerMap.put(safeId, completed);
        return completed;
    }

    @Override
    public StudentTodo cancel(String id, String conversationId) {
        String safeId = requireId(id);
        String safeConversationId = requireConversationId(conversationId);
        var innerMap = todosMap.get(safeConversationId);
        if (innerMap == null) {
            throw new StudentTodoPersistenceException("待办 " + safeId + " 不存在");
        }
        StudentTodo existing = innerMap.get(safeId);
        if (existing == null) {
            throw new StudentTodoPersistenceException("待办 " + safeId + " 不存在");
        }
        if (existing.status() != StudentTodoStatus.PENDING) {
            throw new StudentTodoPersistenceException("待办 " + safeId + " 不是待办状态，不能取消");
        }
        StudentTodo canceled = existing.cancel();
        innerMap.put(safeId, canceled);
        return canceled;
    }

    @Override
    public StudentTodo modifyDueTime(String id, String conversationId, java.time.Instant newDueTime) {
        String safeId = requireId(id);
        String safeConversationId = requireConversationId(conversationId);
        var innerMap = todosMap.get(safeConversationId);
        if (innerMap == null) {
            throw new StudentTodoPersistenceException("待办 " + safeId + " 不存在");
        }
        StudentTodo existing = innerMap.get(safeId);
        if (existing == null) {
            throw new StudentTodoPersistenceException("待办 " + safeId + " 不存在");
        }
        if (existing.status() != StudentTodoStatus.PENDING) {
            throw new StudentTodoPersistenceException("只能修改待办状态的待办");
        }
        java.time.Instant now = java.time.Instant.now();
        StudentTodo modified = new StudentTodo(
                existing.id(), existing.conversationId(), existing.title(),
                newDueTime, existing.status(), existing.source(), existing.createdAt());
        innerMap.put(safeId, modified);
        return modified;
    }

    @Override
    public StudentTodo findById(String id, String conversationId) {
        String safeId = requireId(id);
        String safeConversationId = requireConversationId(conversationId);
        var innerMap = todosMap.get(safeConversationId);
        if (innerMap == null) {
            return null;
        }
        return innerMap.get(safeId);
    }

    @Override
    public Set<String> allConversationIds() {
        return Set.copyOf(todosMap.keySet());
    }

    @Override
    public int deleteCompletedOlderThan(Instant cutoff) {
        int totalDeleted = 0;
        for (ConcurrentHashMap<String, StudentTodo> innerMap : todosMap.values()) {
            int before = innerMap.size();
            innerMap.values().removeIf(todo ->
                    todo.status() == StudentTodoStatus.COMPLETED
                            && todo.createdAt().isBefore(cutoff));
            totalDeleted += before - innerMap.size();
        }
        // Remove empty inner maps
        todosMap.values().removeIf(ConcurrentHashMap::isEmpty);
        return totalDeleted;
    }

    private static void requireValidNew(StudentTodo todo) {
        if (todo.status() != StudentTodoStatus.PENDING) {
            throw new IllegalArgumentException("新建待办必须处于待办状态");
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("待办 ID 不能为空");
        }
        return id.strip();
    }

    private static String requireConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("会话 ID 不能为空");
        }
        return conversationId.strip();
    }
}
