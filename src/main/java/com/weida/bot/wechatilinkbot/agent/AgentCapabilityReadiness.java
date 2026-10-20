package com.weida.bot.wechatilinkbot.agent;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.BROWSE_FILES;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DOCUMENT;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DIAGRAM;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_PRESENTATION;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_SPREADSHEET;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GET_OCS_STUDY_STATUS;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.LEARNING_QUERY;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.SEND_EMAIL;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.START_OCS_STUDY;

import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reports capability readiness at startup and translates stable tool diagnostics into
 * actionable messages suitable for the phone channel.
 */
@Component
public class AgentCapabilityReadiness {

    private static final Logger LOG = LoggerFactory.getLogger(AgentCapabilityReadiness.class);

    private final ToolRegistry toolRegistry;
    private final ObjectProvider<OcsQuestionAnswerer> questionAnswererProvider;

    public AgentCapabilityReadiness(
            ToolRegistry toolRegistry,
            ObjectProvider<OcsQuestionAnswerer> questionAnswererProvider) {
        this.toolRegistry = toolRegistry;
        this.questionAnswererProvider = questionAnswererProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logReadiness() {
        List<ReadinessCheck> checks = List.of(
                new ReadinessCheck("文件目录", BROWSE_FILES, null),
                new ReadinessCheck("QQ SMTP（邮件发送）", SEND_EMAIL, null),
                new ReadinessCheck("学习通", LEARNING_QUERY, null),
                new ReadinessCheck("官方 OCS", START_OCS_STUDY, null),
                new ReadinessCheck("Office 文档", GENERATE_DOCUMENT, null),
                new ReadinessCheck("Office 表格", GENERATE_SPREADSHEET, null),
                new ReadinessCheck("Office 演示文稿", GENERATE_PRESENTATION, null),
                new ReadinessCheck("PlantUML 图表", GENERATE_DIAGRAM, null),
                new ReadinessCheck(
                        "OCS 题库答案来源",
                        null,
                        () -> questionAnswererProvider.getIfAvailable() != null
                                && questionAnswererProvider.getIfAvailable().available()));
        for (ReadinessCheck check : checks) {
            boolean available = check.available(toolRegistry);
            String reason = check.toolName() == null
                    ? available ? "available" : "unavailable"
                    : toolRegistry.availabilityReason(check.toolName());
            if (available) {
                LOG.info("agent_capability_ready capability={}", check.label());
            } else {
                LOG.warn(
                        "agent_capability_unavailable capability={} reason={}",
                        check.label(),
                        reason == null ? "unavailable" : reason);
            }
        }
        LOG.info(
                "agent_capability_summary available={}",
                availableCapabilities(toolRegistry));
    }

    /** Returns a stable user-facing explanation while keeping internal tool names private. */
    public static String unavailableReply(
            ToolRegistry toolRegistry,
            String toolName,
            String fallback) {
        String detail = missingConfiguration(toolName, toolRegistry.availabilityReason(toolName));
        String available = availableCapabilities(toolRegistry);
        if (available.isBlank()) {
            return fallback + " " + detail;
        }
        return fallback + " " + detail + " 当前可用能力：" + available + "。";
    }

    private static String missingConfiguration(String toolName, String reason) {
        String normalizedReason = reason == null ? "" : reason;
        return switch (toolName) {
            case BROWSE_FILES, "select_file" -> switch (normalizedReason) {
                case "disabled" ->
                        "请设置 BOT_FILE_BROWSE_ENABLED=true，并配置允许的本机文件目录后重试。";
                case "allowed_paths_missing" ->
                        "请配置允许的本机文件目录（BOT_FILE_BROWSE_ALLOWED_PATHS）后重试。";
                default -> "请检查文件搜索开关和 BOT_FILE_BROWSE_ALLOWED_PATHS 配置后重试。";
            };
            case SEND_EMAIL -> switch (normalizedReason) {
                case "sender_address_missing" -> "缺少 QQ 发件邮箱（QQ_EMAIL_ADDRESS）。";
                case "authorization_code_missing" -> "缺少 QQ SMTP/IMAP 授权码（QQ_SMTP_IMAP）。";
                case "conversation_allowlist_missing" ->
                        "缺少邮件会话白名单（BOT_EMAIL_ALLOWED_CONVERSATIONS）。";
                case "recipient_allowlist_missing" ->
                        "缺少邮件收件人白名单（BOT_EMAIL_ALLOWED_RECIPIENTS）。";
                case "disabled" -> "QQ 邮件能力未启用（BOT_EMAIL_ENABLED=false）。";
                default -> "请检查 QQ SMTP/IMAP 的启用开关、发件地址、授权码和白名单。";
            };
            case LEARNING_QUERY ->
                    "学习通能力未就绪，请检查 CHAOXING_ENABLED、登录状态和用户标识配置。";
            case START_OCS_STUDY, GET_OCS_STUDY_STATUS ->
                    "官方 OCS 能力未就绪，请检查 BOT_OCS_ENABLED、回环地址和伴随脚本状态。";
            case GENERATE_PRESENTATION ->
                    "请设置 BOT_PPTX_ENABLED=true，并确认 PPTX 输出目录和运行依赖后重启。";
            case GENERATE_DIAGRAM -> switch (normalizedReason) {
                case "disabled" -> "PlantUML 图表未启用（PLANTUML_ENABLED=false）。";
                case "executable_missing" ->
                        "请将 PLANTUML_EXECUTABLE 配置为启动器文件路径，或包含 plantuml.bat/plantuml"
                                + " 的目录，确认后重启。";
                default -> "请检查 PLANTUML_ENABLED 和 PLANTUML_EXECUTABLE 配置后重试。";
            };
            case GENERATE_DOCUMENT, GENERATE_SPREADSHEET ->
                    "Office 能力未就绪，请检查对应启用开关、输出目录和本地 Office 依赖。";
            default -> "这项能力当前未注册或配置不完整，请稍后重试。";
        };
    }

    private static String availableCapabilities(ToolRegistry toolRegistry) {
        List<String> names = new ArrayList<>();
        addIfAvailable(toolRegistry, BROWSE_FILES, "文件搜索", names);
        addIfAvailable(toolRegistry, SEND_EMAIL, "邮件草稿", names);
        addIfAvailable(toolRegistry, LEARNING_QUERY, "学习通课程", names);
        addIfAvailable(toolRegistry, START_OCS_STUDY, "OCS 学习", names);
        addIfAvailable(toolRegistry, GENERATE_DOCUMENT, "Word/PDF", names);
        addIfAvailable(toolRegistry, GENERATE_SPREADSHEET, "Excel", names);
        addIfAvailable(toolRegistry, GENERATE_PRESENTATION, "PPTX", names);
        addIfAvailable(toolRegistry, GENERATE_DIAGRAM, "PlantUML 图表", names);
        return String.join("、", names);
    }

    private static void addIfAvailable(
            ToolRegistry toolRegistry,
            String toolName,
            String label,
            List<String> names) {
        if (toolRegistry.available(toolName)) {
            names.add(label);
        }
    }

    private record ReadinessCheck(
            String label,
            String toolName,
            Supplier<Boolean> supplier) {

        private boolean available(ToolRegistry toolRegistry) {
            return toolName == null
                    ? Boolean.TRUE.equals(supplier.get())
                    : toolRegistry.available(toolName);
        }
    }
}
