package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import com.weida.bot.wechatilinkbot.persistence.mybatis.StudentTodoMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional SQLite student todo storage.
 *
 * <p>Follows {@code SqliteConversationMemory} patterns: programmatic instantiation
 * via {@code StudentTodoMemoryConfiguration}, mandatory transaction manager, and
 * checked DataAccessException wrapping.</p>
 */
public class SqliteStudentTodoMemory implements StudentTodoMemory {

    private static final Logger LOG = LoggerFactory.getLogger(SqliteStudentTodoMemory.class);

    private final StudentTodoMapper mapper;

    public SqliteStudentTodoMemory(StudentTodoMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public StudentTodo add(StudentTodo todo) {
        requireNonNull(todo, "待办不能为空");
        requireValidNew(todo);
        try {
            mapper.insert(todo);
            LOG.debug("student_todo_inserted id={} conversation={}", todo.id(),
                    Integer.toUnsignedString(todo.conversationId().hashCode(), 16));
            return todo;
        } catch (DataAccessException exception) {
            throw storageFailure("创建", exception);
        }
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "conversationTransactionManager")
    public List<StudentTodo> listPending(String conversationId) {
        String safeConversationId = requireConversationId(conversationId);
        try {
            return mapper.listPendingByConversation(safeConversationId);
        } catch (DataAccessException exception) {
            throw storageFailure("读取", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public StudentTodo complete(String id, String conversationId) {
        String safeId = requireId(id);
        String safeConversationId = requireConversationId(conversationId);
        try {
            int rows = mapper.updateStatus(
                    safeId, safeConversationId,
                    StudentTodoStatus.PENDING, StudentTodoStatus.COMPLETED);
            if (rows != 1) {
                throw new StudentTodoPersistenceException(
                        "待办 " + safeId + " 不存在、不属于当前会话或已完成");
            }
            StudentTodo todo = mapper.findById(safeId, safeConversationId);
            if (todo == null) {
                throw new StudentTodoPersistenceException("待办 " + safeId + " 读取失败");
            }
            return todo;
        } catch (DataAccessException exception) {
            throw storageFailure("完成", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public StudentTodo cancel(String id, String conversationId) {
        String safeId = requireId(id);
        String safeConversationId = requireConversationId(conversationId);
        try {
            int rows = mapper.updateStatus(
                    safeId, safeConversationId,
                    StudentTodoStatus.PENDING, StudentTodoStatus.CANCELED);
            if (rows != 1) {
                throw new StudentTodoPersistenceException(
                        "待办 " + safeId + " 不存在、不属于当前会话或不是待办状态");
            }
            StudentTodo todo = mapper.findById(safeId, safeConversationId);
            if (todo == null) {
                throw new StudentTodoPersistenceException("待办 " + safeId + " 读取失败");
            }
            return todo;
        } catch (DataAccessException exception) {
            throw storageFailure("取消", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public StudentTodo modifyDueTime(String id, String conversationId, java.time.Instant newDueTime) {
        String safeId = requireId(id);
        String safeConversationId = requireConversationId(conversationId);
        try {
            int rows = mapper.updateDueTime(safeId, safeConversationId,
                    StudentTodoStatus.PENDING, newDueTime);
            if (rows != 1) {
                throw new StudentTodoPersistenceException(
                        "待办 " + safeId + " 不存在、不属于当前会话或不是待办状态");
            }
            StudentTodo todo = mapper.findById(safeId, safeConversationId);
            if (todo == null) {
                throw new StudentTodoPersistenceException("待办 " + safeId + " 读取失败");
            }
            return todo;
        } catch (DataAccessException exception) {
            throw storageFailure("修改截止时间", exception);
        }
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "conversationTransactionManager")
    public StudentTodo findById(String id, String conversationId) {
        String safeId = requireId(id);
        String safeConversationId = requireConversationId(conversationId);
        try {
            return mapper.findById(safeId, safeConversationId);
        } catch (DataAccessException exception) {
            throw storageFailure("读取", exception);
        }
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "conversationTransactionManager")
    public Set<String> allConversationIds() {
        try {
            return mapper.listAllPending().stream()
                    .map(StudentTodo::conversationId)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (DataAccessException exception) {
            throw storageFailure("读取会话列表", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "conversationTransactionManager")
    public int deleteCompletedOlderThan(Instant cutoff) {
        try {
            return mapper.deleteCompletedOlderThan(cutoff);
        } catch (DataAccessException exception) {
            throw storageFailure("清理", exception);
        }
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

    private static StudentTodoPersistenceException storageFailure(
            String operation,
            DataAccessException exception) {
        return new StudentTodoPersistenceException(
                "SQLite 待办" + operation + "失败", exception);
    }
}
