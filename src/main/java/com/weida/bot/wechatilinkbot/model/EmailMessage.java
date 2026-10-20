package com.weida.bot.wechatilinkbot.model;

import java.util.List;

/**
 * Validated plain-text email payload crossing the Tool and Service boundary.
 *
 * @param recipientList primary recipients
 * @param subject email subject
 * @param body plain-text body
 * @param attachments bounded in-memory attachments
 * @author ryx
 * @since 2026-07-23
 */
public record EmailMessage(
        List<String> recipientList,
        String subject,
        String body,
        List<EmailAttachment> attachments) {

    public EmailMessage {
        if (recipientList == null || recipientList.isEmpty()) {
            throw new IllegalArgumentException("recipientList 不能为空");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject 不能为空");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body 不能为空");
        }
        recipientList = List.copyOf(recipientList);
        subject = subject.strip();
        body = body.strip();
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public EmailMessage(
            List<String> recipientList,
            String subject,
            String body) {
        this(recipientList, subject, body, List.of());
    }
}
