package com.weida.bot.wechatilinkbot.infrastructure.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the Jackson 2 mapper used by provider protocols and tool JSON.
 *
 * <p>Spring Boot 4 uses Jackson 3 for HTTP by default, while the current provider
 * ecosystem still exposes Jackson 2 types. Keeping this adapter bean in infrastructure
 * prevents that compatibility detail from leaking into business modules.</p>
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfiguration {

    @Bean
    ObjectMapper protocolObjectMapper() {
        return new ObjectMapper();
    }
}
