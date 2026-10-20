package com.weida.bot.wechatilinkbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point for the modular-monolith WeChat personal agent.
 *
 * @author ryx
 * @since 2026-07-20
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WechatIlinkBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(WechatIlinkBotApplication.class, args);
    }
}
