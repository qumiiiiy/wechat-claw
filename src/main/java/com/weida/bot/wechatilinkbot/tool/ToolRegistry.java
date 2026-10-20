package com.weida.bot.wechatilinkbot.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.DesktopTaskPolicyViolationException;
import com.weida.bot.wechatilinkbot.capability.EmailDeliveryException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Discovers tools, owns schema/argument conversion, and executes authorized calls.
 */
@Component
public class ToolRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ToolRegistry.class);
    private static final String SPEECH_TOOL_NAME = "generate_speech_video";
    private static final Pattern REASON_CODE_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private static final Pattern JSON_CODE_FENCE_PATTERN = Pattern.compile(
            "(?is)^\\s*```(?:json)?\\s*(.*?)\\s*```\\s*$");

    private final ObjectMapper objectMapper;
    private final Map<String, AgentTool<?>> toolMap;

    public ToolRegistry(List<AgentTool<?>> toolList, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Map<String, AgentTool<?>> mutableToolMap = new LinkedHashMap<>();
        for (AgentTool<?> tool : toolList) {
            AgentTool<?> duplicate = mutableToolMap.putIfAbsent(tool.name(), tool);
            if (duplicate != null) {
                throw new IllegalStateException("Agent Tool 名称重复：" + tool.name());
            }
        }
        toolMap = Collections.unmodifiableMap(new LinkedHashMap<>(mutableToolMap));
        logAvailability();
    }

    public List<ToolDefinition> listDefinitions(
            boolean includeSpeechTool,
            Set<String> allowedToolNames) {
        return toolMap.values().stream()
                .filter(AgentTool::available)
                .filter(tool -> includeSpeechTool || !SPEECH_TOOL_NAME.equals(tool.name()))
                .filter(tool -> isAllowed(tool, allowedToolNames))
                .map(this::definition)
                .toList();
    }

    /**
     * Lists every currently available tool for model-directed planning.
     *
     * <p>The registry still filters disabled tools and the speech tool according to the
     * caller. The older {@link #listDefinitions(boolean, Set)} method remains available for
     * narrow, server-directed workflows that intentionally hide explicit-intent tools.</p>
     */
    public List<ToolDefinition> listModelDefinitions(boolean includeSpeechTool) {
        return listDefinitions(includeSpeechTool, null);
    }

    public boolean available(String toolName) {
        AgentTool<?> tool = toolMap.get(toolName);
        return tool != null && tool.available();
    }

    public String availabilityReason(String toolName) {
        AgentTool<?> tool = toolMap.get(toolName);
        if (tool == null) {
            return "not_registered";
        }
        if (tool.available()) {
            return "available";
        }
        String reason = tool.availabilityReason();
        return reason != null
                        && !"available".equals(reason)
                        && REASON_CODE_PATTERN.matcher(reason).matches()
                ? reason
                : "unavailable";
    }

    public ToolInvocation execute(
            String toolName,
            String arguments,
            ToolRunContext runContext) {
        AgentTool<?> tool = toolMap.get(toolName);
        if (tool == null || !tool.available()) {
            throw new IllegalArgumentException("模型请求了未授权或不可用的工具");
        }
        long startNanos = System.nanoTime();
        InvocationOutcome outcome = invoke(tool, arguments, runContext);
        ToolExecution execution = outcome.execution();
        runContext.record(toolName, execution, outcome.succeeded());
        LOG.info(
                "agent_tool_execution_completed tool={} succeeded={} generatedMedia={} "
                        + "elapsedMillis={}",
                toolName,
                outcome.succeeded(),
                execution.generatedMedia() == null
                        ? "none"
                        : execution.generatedMedia().kind(),
                elapsedMillis(startNanos));
        return new ToolInvocation(
                execution.content(),
                tool.returnDirect(),
                outcome.succeeded(),
                outcome.retryable());
    }

    public void clearConversation(String conversationId) {
        toolMap.values().forEach(tool -> tool.clearConversation(conversationId));
    }

    private ToolDefinition definition(AgentTool<?> tool) {
        return new ToolDefinition(
                tool.name(),
                tool.description(),
                ToolSchemaGenerator.generate(tool.inputType(), objectMapper),
                tool.returnDirect());
    }

    private static boolean isAllowed(AgentTool<?> tool, Set<String> allowedToolNames) {
        if (allowedToolNames == null) {
            return true;
        }
        return allowedToolNames.contains(tool.name()) || !tool.requiresExplicitIntent();
    }

    private InvocationOutcome invoke(
            AgentTool<?> tool,
            String arguments,
            ToolRunContext runContext) {
        if (tool.producesMedia() && !runContext.mediaAllowed()) {
            return new InvocationOutcome(
                    error("本次回复的媒体生成数量已达到上限", tool.returnDirect()),
                    false,
                    false);
        }
        try {
            ToolExecution execution = invokeTyped(tool, arguments, runContext.trustedContext());
            boolean failed = hasErrorStatus(execution.content());
            return new InvocationOutcome(
                    execution,
                    !failed,
                    failed && hasRetryableError(execution.content()));
        } catch (IllegalArgumentException exception) {
            LOG.warn(
                    "agent_tool_input_rejected tool={} reason={}",
                    tool.name(),
                    abbreviate(exception.getMessage(), 160));
            return new InvocationOutcome(
                    error(abbreviate(exception.getMessage(), 300), tool.returnDirect()),
                    false,
                    !(exception instanceof DesktopTaskPolicyViolationException));
        } catch (EmailDeliveryException exception) {
            LOG.error(
                    "agent_tool_external_delivery_failed tool={}",
                    tool.name(),
                    sanitizedFailure(exception));
            return new InvocationOutcome(
                    error(
                            "邮件发送结果异常；请先检查 QQ 已发送邮件，确认未送达后再重新准备",
                            tool.returnDirect()),
                    false,
                    false);
        } catch (RuntimeException exception) {
            LOG.error(
                    "agent_tool_execution_failed tool={}",
                    tool.name(),
                    sanitizedFailure(exception));
            return new InvocationOutcome(
                    error("工具执行失败，请稍后再试", tool.returnDirect()),
                    false,
                    false);
        }
    }

    private boolean hasErrorStatus(String content) {
        try {
            return "error".equals(objectMapper.readTree(content).path("status").asText());
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean hasRetryableError(String content) {
        try {
            return objectMapper.readTree(content).path("retryable").asBoolean(false);
        } catch (Exception exception) {
            return false;
        }
    }

    private <I> ToolExecution invokeTyped(
            AgentTool<I> tool,
            String arguments,
            ToolContext context) {
        try {
            String safeArguments = normalizeArguments(arguments);
            I input = objectMapper.readValue(safeArguments, tool.inputType());
            return tool.execute(input, context);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("工具参数不是有效 JSON", exception);
        }
    }

    private static String normalizeArguments(String arguments) {
        String normalized = arguments == null || arguments.isBlank() ? "{}" : arguments.strip();
        Matcher matcher = JSON_CODE_FENCE_PATTERN.matcher(normalized);
        return matcher.matches() ? matcher.group(1).strip() : normalized;
    }

    private ToolExecution error(String message, boolean returnDirect) {
        String safeMessage = message == null || message.isBlank() ? "工具执行失败" : message;
        if (returnDirect) {
            return new ToolExecution(safeMessage, null);
        }
        String content = objectMapper.createObjectNode()
                .put("status", "error")
                .put("message", safeMessage)
                .toString();
        return new ToolExecution(content, null);
    }

    private void logAvailability() {
        toolMap.values().forEach(tool -> LOG.info(
                "agent_tool_registered tool={} available={} availabilityReason={}",
                tool.name(),
                tool.available(),
                availabilityReason(tool.name())));
    }

    private static RuntimeException sanitizedFailure(RuntimeException exception) {
        RuntimeException sanitized = new RuntimeException(
                exception.getClass().getSimpleName());
        sanitized.setStackTrace(exception.getStackTrace());
        return sanitized;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "…";
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private record InvocationOutcome(
            ToolExecution execution,
            boolean succeeded,
            boolean retryable) {
    }
}
