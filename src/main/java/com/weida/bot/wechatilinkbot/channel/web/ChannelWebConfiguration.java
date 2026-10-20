package com.weida.bot.wechatilinkbot.channel.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers static-page routes without embedding frontend content in controllers.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Configuration(proxyBeanMethods = false)
public class ChannelWebConfiguration implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/wechat/login")
                .setViewName("forward:/wechat/login.html");
    }
}
