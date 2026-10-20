package com.weida.bot.wechatilinkbot.persistence;

import com.weida.bot.wechatilinkbot.persistence.mybatis.ConversationMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic {@link ConversationMemory} bean factory.
 *
 * <p>This configuration replaces the conditional-annotation approach
 * ({@code @ConditionalOnProperty}, {@code @ConditionalOnBean})
 * because those annotations check bean <em>definitions</em> rather than
 * actual bean instances, causing ordering issues when the MyBatis mapper
 * is registered but cannot be instantiated.</p>
 */
@Configuration(proxyBeanMethods = false)
public class ConversationMemoryConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(
            ConversationMemoryConfiguration.class);

    @Bean
    ConversationMemory conversationMemory(
            ConversationMemoryProperties properties,
            ConfigurableListableBeanFactory beanFactory) {
        if ("sqlite".equals(properties.getType())) {
            try {
                ConversationMessageMapper mapper =
                        beanFactory.getBean(ConversationMessageMapper.class);
                LOG.info(
                        "conversation_memory_provider_selected provider=sqlite");
                return new SqliteConversationMemory(mapper, properties);
            } catch (Exception exception) {
                LOG.warn(
                        "conversation_sqlite_unavailable type={} reason={} — "
                                + "falling_back_to_in_memory",
                        properties.getType(),
                        exception.getMessage());
            }
        }
        LOG.info(
                "conversation_memory_provider_selected provider=in_memory "
                        + "type={}",
                properties.getType());
        return new InMemoryConversationMemory(properties);
    }
}
