package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 学习通自动配置。
 * ChaoxingProperties 由主应用的 @ConfigurationPropertiesScan 自动注册。
 */
@Configuration
public class ChaoxingConfiguration {

    @Bean
    public ChaoxingClient chaoxingClient(
            ChaoxingProperties properties,
            CourseJsonParser courseJsonParser,
            TaskListParser taskListParser,
            ObjectMapper objectMapper,
            ChaoxingHttpGateway httpGateway) {
        return new ChaoxingClient(
                properties, courseJsonParser, taskListParser, objectMapper, httpGateway);
    }
}
