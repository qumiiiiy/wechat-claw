package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.SEND_EMAIL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.EmailCapability;
import com.weida.bot.wechatilinkbot.model.EmailMessage;
import com.weida.bot.wechatilinkbot.model.EmailAttachment;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.tool.input.EmailToolInput;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Exposes two-step, allowlisted plain-text email delivery.
 */
@Order(50)
@Component
public class EmailTool implements AgentTool<EmailToolInput> {

    private static final String PREPARE_ACTION = "prepare";
    private static final String CONFIRM_ACTION = "confirm";
    private static final String CLARIFY_ACTION = "clarify";

    private final EmailCapability emailCapability;
    private final ObjectMapper objectMapper;

    public EmailTool(
            EmailCapability emailCapability,
            ObjectMapper objectMapper) {
        this.emailCapability = emailCapability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return SEND_EMAIL;
    }

    @Override
    public String description() {
        return "发送邮件（可包含本会话最近生成文件）的两阶段工具。当前消息明确要求发邮件且收件人、主题、正文齐全时，"
                + "先用 prepare 保存草稿并展示；只有用户下一条消息单独回复“确认发送”时才用 confirm。"
                + "信息不全或正文依赖尚未查询的实时数据时用 clarify；咨询、代写或取消时不要调用。";
    }

    @Override
    public Class<EmailToolInput> inputType() {
        return EmailToolInput.class;
    }

    @Override
    public boolean available() {
        return emailCapability.available();
    }

    @Override
    public String availabilityReason() {
        return emailCapability.availabilityReason();
    }

    @Override
    public boolean requiresExplicitIntent() {
        return true;
    }

    @Override
    public boolean producesMedia() {
        return false;
    }

    @Override
    public boolean returnDirect() {
        return true;
    }

    @Override
    public ToolExecution execute(EmailToolInput input, ToolContext context) {
        return switch (input.action()) {
            case PREPARE_ACTION -> prepare(input, context);
            case CONFIRM_ACTION -> confirm(context);
            case CLARIFY_ACTION -> clarify();
            default -> throw new IllegalArgumentException(
                    "邮件工具 action 仅支持 prepare、confirm 或 clarify");
        };
    }

    @Override
    public void clearConversation(String conversationId) {
        emailCapability.clearPendingEmail(conversationId);
    }

    private ToolExecution prepare(EmailToolInput input, ToolContext context) {
        if (context.weatherInformationRequested()
                && !context.weatherInformationResolved()) {
            throw new IllegalArgumentException(
                    "邮件正文依赖实时天气，但本轮尚未执行天气工具；请先确认地点并查询天气");
        }
        EmailMessage request = new EmailMessage(
                input.recipientList(),
                input.subject(),
                input.body(),
                attachments(context));
        EmailMessage prepared = emailCapability.prepareEmail(
                context.conversationId(),
                context.currentUserText(),
                context.emailSendRequested(),
                request);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "approval_required");
        addDraft(result, prepared);
        result.put(
                "message",
                "邮件尚未发送。请展示草稿并要求用户下一条消息单独回复“确认发送”；"
                        + "任何修改都必须重新 prepare。");
        return new ToolExecution(result.toString(), null);
    }

    private ToolExecution confirm(ToolContext context) {
        EmailMessage sent = emailCapability.confirmAndSendEmail(
                context.conversationId(),
                context.currentUserText());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "sent");
        result.put("recipient_count", sent.recipientList().size());
        result.put("subject", sent.subject());
        result.put("message", "邮件已通过 QQ SMTP 发送");
        return new ToolExecution(result.toString(), null);
    }

    private ToolExecution clarify() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "information_required");
        result.put(
                "message",
                "邮件尚未创建草稿且尚未发送。请只询问缺失信息；"
                        + "如正文依赖实时数据，先调用对应查询工具。");
        return new ToolExecution(result.toString(), null);
    }

    private void addDraft(ObjectNode result, EmailMessage request) {
        ArrayNode recipients = result.putArray("recipient_list");
        request.recipientList().forEach(recipients::add);
        result.put("subject", request.subject());
        result.put("body", request.body());
        result.put("body_characters", request.body().length());
        ArrayNode attachments = result.putArray("attachments");
        request.attachments().forEach(attachment -> attachments.add(attachment.fileName()));
        result.put("approval_expires_seconds", emailCapability.approvalExpireSeconds());
    }

    private List<EmailAttachment> attachments(ToolContext context) {
        if (!context.emailAttachmentRequested()) {
            return List.of();
        }
        String requestedExtension = requestedExtension(context.currentUserText());
        List<EmailAttachment> attachments = context.availableGeneratedFiles().stream()
                .filter(media -> media.kind() == GeneratedMediaKind.FILE)
                .filter(media -> requestedExtension.isBlank()
                        || media.fileName().toLowerCase(java.util.Locale.ROOT)
                                .endsWith(requestedExtension))
                .map(EmailTool::toAttachment)
                .toList();
        if (attachments.isEmpty()) {
            throw new IllegalArgumentException(
                    "未找到可附加的最近生成文件，请先生成所需的 PDF 或其他文件");
        }
        return attachments;
    }

    private static String requestedExtension(String text) {
        String normalized = text.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("pdf")) {
            return ".pdf";
        }
        if (normalized.contains("docx") || normalized.contains("word")) {
            return ".docx";
        }
        if (normalized.contains("xlsx")
                || normalized.contains("excel")
                || normalized.contains("电子表格")
                || normalized.contains("工作簿")) {
            return ".xlsx";
        }
        return "";
    }

    private static EmailAttachment toAttachment(GeneratedMedia media) {
        return new EmailAttachment(
                media.fileName(),
                contentType(media.fileName()),
                media.bytes());
    }

    private static String contentType(String fileName) {
        String lowerCaseName = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lowerCaseName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerCaseName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lowerCaseName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lowerCaseName.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }
}
