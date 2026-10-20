package com.weida.bot.wechatilinkbot.infrastructure.mail;

import com.weida.bot.wechatilinkbot.model.EmailMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Isolates QQ SMTP delivery from the business approval and Tool layers.
 *
 * @author ryx
 * @since 2026-07-23
 */
@Component
public class QqMailClient {

    private static final Logger LOG = LoggerFactory.getLogger(QqMailClient.class);

    private final QqMailProperties properties;
    private final JavaMailSender mailSender;

    public QqMailClient(QqMailProperties properties, JavaMailSender mailSender) {
        this.properties = properties;
        this.mailSender = mailSender;
    }

    /**
     * Sends one validated plain-text message without logging addresses or content.
     *
     * @param request approved email request
     */
    public void send(EmailMessage request) {
        long startNanos = System.nanoTime();
        try {
            if (request.attachments().isEmpty()) {
                sendPlainText(request);
            } else {
                sendMultipart(request);
            }
            LOG.info(
                    "qq_mail_delivery_completed recipientCount={} subjectLength={} bodyLength={} "
                            + "attachmentCount={} elapsedMillis={}",
                    request.recipientList().size(),
                    request.subject().length(),
                    request.body().length(),
                    request.attachments().size(),
                    elapsedMillis(startNanos));
        } catch (MailException exception) {
            throw new QqMailDeliveryException(
                    "QQ SMTP 发送失败，请检查邮箱服务和授权码",
                    exception);
        }
    }

    private void sendPlainText(EmailMessage request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getSenderAddress());
        message.setTo(request.recipientList().toArray(new String[0]));
        message.setSubject(request.subject());
        message.setText(request.body());
        mailSender.send(message);
    }

    private void sendMultipart(EmailMessage request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getSenderAddress());
            helper.setTo(request.recipientList().toArray(new String[0]));
            helper.setSubject(request.subject());
            helper.setText(request.body(), false);
            for (var attachment : request.attachments()) {
                helper.addAttachment(
                        attachment.fileName(),
                        new jakarta.mail.util.ByteArrayDataSource(
                                attachment.bytes(),
                                attachment.contentType()));
            }
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new QqMailDeliveryException(
                    "QQ 邮件附件构造失败，请稍后重试",
                    exception);
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
