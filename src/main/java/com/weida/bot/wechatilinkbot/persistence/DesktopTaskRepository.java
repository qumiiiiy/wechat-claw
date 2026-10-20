package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.model.DesktopTask;
import com.weida.bot.wechatilinkbot.persistence.mybatis.DesktopTaskMapper;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** SQLite-backed storage for {@link DesktopTask} lifecycle state. */
@Component
@ConditionalOnProperty(prefix = "bot.persistence.conversation", name = "type", havingValue = "sqlite")
public class DesktopTaskRepository {

    private final DesktopTaskMapper mapper;

    public DesktopTaskRepository(DesktopTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(transactionManager = "conversationTransactionManager")
    public DesktopTask create(DesktopTask task) {
        try {
            mapper.insert(task);
            return task;
        } catch (DataAccessException exception) {
            throw storageFailure("创建", exception);
        }
    }

    @Transactional(transactionManager = "conversationTransactionManager")
    public DesktopTask update(DesktopTask task) {
        try {
            if (mapper.update(task) != 1) {
                throw new DesktopTaskPersistenceException("更新桌面任务失败：任务不存在", null);
            }
            return task;
        } catch (DataAccessException exception) {
            throw storageFailure("更新", exception);
        }
    }

    @Transactional(readOnly = true, transactionManager = "conversationTransactionManager")
    public Optional<DesktopTask> findById(String taskId) {
        try {
            return Optional.ofNullable(mapper.findById(taskId));
        } catch (DataAccessException exception) {
            throw storageFailure("读取", exception);
        }
    }

    private static DesktopTaskPersistenceException storageFailure(
            String operation,
            DataAccessException exception) {
        return new DesktopTaskPersistenceException("SQLite 桌面任务" + operation + "失败", exception);
    }
}
