package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.persistence.mybatis.StudentTodoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic {@link StudentTodoMemory} bean factory.
 *
 * <p>Follows {@code ConversationMemoryConfiguration}: tries SQLite first
 * (when the mapper bean is available), falls back to in-memory storage.</p>
 */
@Configuration(proxyBeanMethods = false)
public class StudentTodoMemoryConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(
            StudentTodoMemoryConfiguration.class);

    @Bean
    StudentTodoMemory studentTodoMemory(
            ConversationMemoryProperties properties,
            ConfigurableListableBeanFactory beanFactory) {
        if ("sqlite".equals(properties.getType())) {
            try {
                StudentTodoMapper mapper =
                        beanFactory.getBean(StudentTodoMapper.class);
                LOG.info("student_todo_memory_provider_selected provider=sqlite");
                return new SqliteStudentTodoMemory(mapper);
            } catch (Exception exception) {
                LOG.warn(
                        "student_todo_sqlite_unavailable type={} reason={} — "
                                + "falling_back_to_in_memory",
                        properties.getType(),
                        exception.getMessage());
            }
        }
        LOG.info("student_todo_memory_provider_selected provider=in_memory type={}",
                properties.getType());
        return new InMemoryStudentTodoMemory();
    }
}
