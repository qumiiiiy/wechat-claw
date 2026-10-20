package com.weida.bot.wechatilinkbot.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class QqMailConfigurationTest {

    @Test
    void fixesQqSmtpHostSslAndBoundedTimeouts() {
        QqMailProperties properties = new QqMailProperties();
        properties.setSenderAddress("sender@qq.com");
        properties.setAuthorizationCode("authorization-code");

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) new QqMailConfiguration()
                .qqMailSender(properties);

        assertThat(mailSender.getHost()).isEqualTo("smtp.qq.com");
        assertThat(mailSender.getPort()).isEqualTo(465);
        assertThat(mailSender.getUsername()).isEqualTo("sender@qq.com");
        assertThat(mailSender.getPassword()).isEqualTo("authorization-code");
        assertThat(mailSender.getJavaMailProperties())
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.ssl.enable", "true")
                .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                .containsEntry("mail.smtp.connectiontimeout", "10000")
                .containsEntry("mail.smtp.timeout", "20000")
                .containsEntry("mail.smtp.writetimeout", "20000");
    }
}
