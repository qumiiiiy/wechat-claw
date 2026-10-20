package com.weida.bot.wechatilinkbot.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.weida.bot.wechatilinkbot.model.EmailMessage;
import com.weida.bot.wechatilinkbot.model.EmailAttachment;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class QqMailClientTest {

    @Test
    void mapsApprovedPlainTextEmailToMailSender() {
        QqMailProperties properties = new QqMailProperties();
        properties.setSenderAddress("sender@qq.com");
        JavaMailSender mailSender = mock(JavaMailSender.class);
        QqMailClient client = new QqMailClient(properties, mailSender);
        EmailMessage request = new EmailMessage(
                List.of("first@example.com", "second@example.com"),
                "项目进展",
                "本周任务已完成。");

        client.send(request);

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo("sender@qq.com");
        assertThat(message.getTo())
                .containsExactly("first@example.com", "second@example.com");
        assertThat(message.getSubject()).isEqualTo("项目进展");
        assertThat(message.getText()).isEqualTo("本周任务已完成。");
    }

    @Test
    void mapsApprovedEmailAttachmentToMimeMessage() throws Exception {
        QqMailProperties properties = new QqMailProperties();
        properties.setSenderAddress("sender@qq.com");
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        QqMailClient client = new QqMailClient(properties, mailSender);
        EmailMessage request = new EmailMessage(
                List.of("first@example.com"),
                "西湖行程",
                "详见附件。",
                List.of(new EmailAttachment("西湖行程.pdf", "application/pdf", new byte[] {1, 2, 3})));

        client.send(request);

        org.mockito.Mockito.verify(mailSender).send(mimeMessage);
        Multipart multipart = (Multipart) mimeMessage.getContent();
        assertThat(multipart.getCount()).isGreaterThanOrEqualTo(2);
        jakarta.mail.BodyPart attachment = null;
        for (int index = 0; index < multipart.getCount(); index++) {
            if ("西湖行程.pdf".equals(multipart.getBodyPart(index).getFileName())) {
                attachment = multipart.getBodyPart(index);
                break;
            }
        }
        assertThat(attachment).isNotNull();
        assertThat(attachment.getDataHandler().getContentType()).startsWith("application/pdf");
    }
}
