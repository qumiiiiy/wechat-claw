package com.weida.bot.wechatilinkbot.agent;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DOCUMENT;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_IMAGE;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_SPREADSHEET;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_TEXT_FILE;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.SEND_EMAIL;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.MediaCapability;
import com.weida.bot.wechatilinkbot.model.AgentReply;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.tool.ToolExecution;
import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import com.weida.bot.wechatilinkbot.tool.ToolRunContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Applies user-visible fallback and high-risk workflow policies after the model loop.
 */
@Component
public class AgentResponsePolicy {

    private static final Logger LOG = LoggerFactory.getLogger(AgentResponsePolicy.class);
    private static final int MAX_GENERATED_MEDIA = 2;
    private static final String DEFAULT_MEDIA_REPLY = "已按你的要求生成完成。";
    private static final String IMAGE_FAILURE_REPLY = "这次图片没有成功生成，请稍后再试。";
    private static final String FILE_FAILURE_REPLY = "这次文件没有成功生成，请稍后再试。";
    private static final String SPREADSHEET_FAILURE_REPLY =
            "这次 Excel 工作簿没有成功生成，请检查功能是否已启用后重试。";
    private static final String EMAIL_TOOL_UNAVAILABLE_REPLY =
            "邮件未发送：邮件发送功能当前未启用或配置不完整。";
    private static final String EMAIL_TOOL_NOT_EXECUTED_REPLY =
            "邮件未发送：本轮没有成功执行邮件工具，请重新发起邮件请求。";

    private final MediaCapability mediaCapability;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final AgentPromptAssembler promptAssembler;

    public AgentResponsePolicy(
            MediaCapability mediaCapability,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            AgentPromptAssembler promptAssembler) {
        this.mediaCapability = mediaCapability;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.promptAssembler = promptAssembler;
    }

    public AgentReply finish(
            String assistantContent,
            ToolRunContext runContext,
            AgentIntent intent,
            String conversationReference) throws IOException {
        List<GeneratedMedia> generatedMedia =
                new ArrayList<>(runContext.generatedMedia());
        String content = promptAssembler.sanitize(assistantContent);
        if (!generatedMedia.isEmpty() && isToolResult(content)) {
            content = "";
        }
        if (imageGenerationAttempted(runContext)
                && !contains(generatedMedia, GeneratedMediaKind.IMAGE)) {
            LOG.warn(
                    "agent_requested_image_missing conversation={} toolAvailable={}",
                    conversationReference,
                    toolRegistry.available(GENERATE_IMAGE));
            content = IMAGE_FAILURE_REPLY;
        }
        if (fileGenerationAttempted(runContext)
                && !contains(generatedMedia, GeneratedMediaKind.FILE)) {
            LOG.warn(
                    "agent_requested_text_file_missing conversation={} "
                            + "textFileToolAvailable={} documentToolAvailable={}",
                    conversationReference,
                    toolRegistry.available(GENERATE_TEXT_FILE),
                    toolRegistry.available(GENERATE_DOCUMENT));
            content = FILE_FAILURE_REPLY;
        }
        boolean spreadsheetAvailable = toolRegistry.available(GENERATE_SPREADSHEET);
        boolean spreadsheetMissing = (spreadsheetGenerationAttempted(runContext)
                || intent.spreadsheetRequested()
                && !spreadsheetAvailable)
                && !contains(generatedMedia, GeneratedMediaKind.FILE);
        if (spreadsheetMissing) {
            LOG.warn(
                    "agent_requested_spreadsheet_missing conversation={} toolAvailable={}",
                    conversationReference,
                    spreadsheetAvailable);
            content = SPREADSHEET_FAILURE_REPLY;
        }
        if (intent.emailSendRequested() && !spreadsheetMissing) {
            content = emailReply(content, intent.emailConfirmation(), runContext);
        }
        if (content.isBlank() && generatedMedia.isEmpty()) {
            throw new IOException("模型返回了空内容");
        }
        if (content.isBlank()) {
            content = DEFAULT_MEDIA_REPLY;
        }
        if (intent.speechRequested()
                && mediaCapability.isSpeechAvailable()
                && !contains(generatedMedia, GeneratedMediaKind.SPEECH_VIDEO)) {
            reserveMediaSlot(generatedMedia);
            generatedMedia.add(mediaCapability.generateSpeechVideo(
                    content,
                    intent.voicePreference()));
        }
        return new AgentReply(content, generatedMedia);
    }

    private String emailReply(
            String modelContent,
            boolean emailConfirmation,
            ToolRunContext runContext) {
        if (!toolRegistry.available(SEND_EMAIL)) {
            return EMAIL_TOOL_UNAVAILABLE_REPLY;
        }
        Optional<ToolExecution> executionOptional = runContext.latestExecution(SEND_EMAIL);
        if (executionOptional.isEmpty()) {
            return EMAIL_TOOL_NOT_EXECUTED_REPLY;
        }
        JsonNode result = parse(executionOptional.orElseThrow());
        return switch (result.path("status").asText()) {
            case "sent" -> "邮件已发送。";
            case "approval_required" -> approvalRequiredReply(result);
            case "information_required" -> {
                String clarification = modelContent.isBlank()
                        ? "请补充邮件所需的信息；若正文依赖实时数据，请先提供查询地点。"
                        : modelContent;
                yield "邮件尚未创建草稿，也未发送。\n" + clarification;
            }
            default -> {
                String prefix = emailConfirmation ? "邮件未发送：" : "邮件草稿未创建：";
                yield prefix + safeToolMessage(result);
            }
        };
    }

    private JsonNode parse(ToolExecution execution) {
        try {
            return objectMapper.readTree(execution.content());
        } catch (IOException exception) {
            return objectMapper.createObjectNode()
                    .put("status", "error")
                    .put("message", "邮件工具返回了无法解析的结果");
        }
    }

    private boolean isToolResult(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        try {
            JsonNode result = objectMapper.readTree(content);
            return result.isObject() && result.hasNonNull("status");
        } catch (IOException exception) {
            return false;
        }
    }

    private static String approvalRequiredReply(JsonNode result) {
        List<String> recipients = new ArrayList<>();
        JsonNode recipientArray = result.path("recipient_list");
        if (recipientArray.isArray()) {
            recipientArray.forEach(recipient -> {
                if (recipient.isTextual() && !recipient.asText().isBlank()) {
                    recipients.add(recipient.asText());
                }
            });
        }
        String recipientText = recipients.isEmpty() ? "未提供" : String.join("、", recipients);
        return "邮件尚未发送，请核对草稿：\n"
                + "收件人：" + recipientText + "\n"
                + "主题：" + result.path("subject").asText("未提供") + "\n"
                + attachmentText(result)
                + "正文：\n" + result.path("body").asText("未提供") + "\n\n"
                + "如确认无误，请在下一条消息中单独回复“确认发送”。";
    }

    private static String attachmentText(JsonNode result) {
        JsonNode attachments = result.path("attachments");
        if (!attachments.isArray() || attachments.isEmpty()) {
            return "附件：无\n";
        }
        List<String> fileNames = new ArrayList<>();
        attachments.forEach(attachment -> {
            if (attachment.isTextual() && !attachment.asText().isBlank()) {
                fileNames.add(attachment.asText());
            }
        });
        return "附件：" + (fileNames.isEmpty() ? "无" : String.join("、", fileNames)) + "\n";
    }

    private static String safeToolMessage(JsonNode result) {
        String message = result.path("message").asText();
        return message.isBlank() ? "邮件工具执行失败" : message;
    }

    private static boolean contains(
            List<GeneratedMedia> generatedMedia,
            GeneratedMediaKind kind) {
        return generatedMedia.stream().anyMatch(media -> media.kind() == kind);
    }

    private boolean imageGenerationAttempted(ToolRunContext runContext) {
        return runContext.latestExecution(GENERATE_IMAGE).isPresent();
    }

    private boolean fileGenerationAttempted(ToolRunContext runContext) {
        return runContext.latestExecution(GENERATE_TEXT_FILE).isPresent()
                || runContext.latestExecution(GENERATE_DOCUMENT).isPresent();
    }

    private boolean spreadsheetGenerationAttempted(ToolRunContext runContext) {
        return runContext.latestExecution(GENERATE_SPREADSHEET).isPresent();
    }

    private static void reserveMediaSlot(List<GeneratedMedia> generatedMedia) {
        if (generatedMedia.size() >= MAX_GENERATED_MEDIA) {
            generatedMedia.removeLast();
        }
    }
}
