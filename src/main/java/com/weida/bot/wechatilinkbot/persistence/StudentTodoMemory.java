package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.StudentTodo;
import java.util.List;

/**
 * Stores and retrieves student todo items independently of the backend.
 *
 * <p>All operations are scoped to a single conversation; sessions are never
 * allowed to cross-read or cross-modify each other's data.</p>
 */
public interface StudentTodoMemory {

    /** Persists a new pending todo. The caller must supply a unique ID. */
    StudentTodo add(StudentTodo todo);

    /** Returns all pending todos for the given conversation, ordered by due time ascending. */
    List<StudentTodo> listPending(String conversationId);

    /** Marks one todo as completed. The todo must exist, be pending, and belong to the conversation. */
    StudentTodo complete(String id, String conversationId);

    /** Cancels one todo. The todo must exist, be pending, and belong to the conversation. */
    StudentTodo cancel(String id, String conversationId);

    /** Changes the due time of one pending todo. Returns the updated todo. */
    StudentTodo modifyDueTime(String id, String conversationId, java.time.Instant newDueTime);

    /** Returns the todo with the given ID, or {@code null} when not found in this conversation. */
    StudentTodo findById(String id, String conversationId);

    /** Returns all conversations that have at least one todo. */
    java.util.Set<String> allConversationIds();

    /** Deletes todos that were completed before the given cutoff time. Returns the number deleted. */
    int deleteCompletedOlderThan(java.time.Instant cutoff);
}
