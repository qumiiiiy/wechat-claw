package com.weida.bot.wechatilinkbot.infrastructure.mail;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Creates the fixed-host QQ SMTP adapter with bounded TLS network operations.
 *
 * @author ryx
 * @since 2026-07-23
 */
@Configuration
public class QqMailConfiguration {

    private static final String QQ_SMTP_HOST = "smtp.qq.com";
    private static final int QQ_SMTP_SSL_PORT = 465;

    @Bean
    public JavaMailSender qqMailSender(QqMailProperties properties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(QQ_SMTP_HOST);
        mailSender.setPort(QQ_SMTP_SSL_PORT);
        mailSender.setUsername(properties.getSenderAddress());
        mailSender.setPassword(properties.getAuthorizationCode());
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties mailProperties = mailSender.getJavaMailProperties();
        mailProperties.setProperty("mail.smtp.auth", "true");
        mailProperties.setProperty("mail.smtp.ssl.enable", "true");
        mailProperties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        mailProperties.setProperty("mail.smtp.starttls.enable", "false");
        mailProperties.setProperty(
                "mail.smtp.connectiontimeout",
                Long.toString(properties.getConnectTimeout().toMillis()));
        mailProperties.setProperty(
                "mail.smtp.timeout",
                Long.toString(properties.getReadTimeout().toMillis()));
        mailProperties.setProperty(
                "mail.smtp.writetimeout",
                Long.toString(properties.getWriteTimeout().toMillis()));
        return mailSender;
    }
}
