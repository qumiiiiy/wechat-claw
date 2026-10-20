package com.weida.bot.wechatilinkbot.agent;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DOCUMENT;

import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import com.weida.bot.wechatilinkbot.tool.ToolInvocation;
import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import com.weida.bot.wechatilinkbot.tool.ToolRunContext;
import com.weida.bot.wechatilinkbot.model.MediaPart;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Provider-neutral Function Calling loop owned by the agent module.
 */
@Component
public class AgentToolLoop {

    private static final Logger LOG = LoggerFactory.getLogger(AgentToolLoop.class);

    private final AgentModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final AgentProperties properties;
    private final LongSupplier nanoTime;

    @Autowired
    public AgentToolLoop(
            AgentModelClient modelClient,
            ToolRegistry toolRegistry,
            AgentProperties properties) {
        this(modelClient, toolRegistry, properties, System::nanoTime);
    }

    AgentToolLoop(
            AgentModelClient modelClient,
            ToolRegistry toolRegistry,
            AgentProperties properties,
            LongSupplier nanoTime) {
        this.modelClient = modelClient;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.nanoTime = nanoTime;
    }

    public String complete(
            List<ModelMessage> initialMessages,
            List<ToolDefinition> toolDefinitions,
            List<String> requiredToolNames,
            ToolRunContext runContext,
            String conversationReference) throws IOException {
        List<ModelMessage> messages = new ArrayList<>(initialMessages);
        Set<String> executedCallFingerprints = new HashSet<>();
        long runStartNanos = nanoTime.getAsLong();
        List<String> requiredTools = normalizeRequiredTools(
                requiredToolNames,
                toolDefinitions);
        for (int toolRound = 0; ; toolRound++) {
            ensureWithinRunLimit(runStartNanos);
            Optional<String> nextRequiredTool = nextRequiredTool(
                    requiredTools,
                    runContext);
            List<ToolDefinition> requestToolDefinitions = definitionsForRound(
                    toolDefinitions,
                    nextRequiredTool);
            long startNanos = System.nanoTime();
            ModelResponse response = modelClient.complete(new ModelRequest(
                    messages,
                    requestToolDefinitions,
                    nextRequiredTool.isPresent()));
            ensureWithinRunLimit(runStartNanos);
            LOG.info(
                    "agent_model_response_received conversation={} contentLength={} "
                            + "toolCalls={} requiredTool={} elapsedMillis={}",
                    conversationReference,
                    response.text().length(),
                    response.toolCalls().size(),
                    nextRequiredTool.orElse("none"),
                    elapsedMillis(startNanos));
            if (!response.hasToolCalls()) {
                if (nextRequiredTool.isPresent()) {
                    throw new IOException("模型未执行本轮必需工具");
                }
                return response.text();
            }
            if (toolRound >= properties.getMaxToolRounds()) {
                throw new IOException("模型工具调用次数超过限制");
            }
            messages.add(ModelMessage.assistantToolCalls(
                    response.text(),
                    response.toolCalls()));
            String directContent = executeCalls(
                    response,
                    requestToolDefinitions,
                    requiredTools,
                    runContext,
                    messages,
                    executedCallFingerprints,
                    runStartNanos,
                    conversationReference);
            if (directContent != null) {
                return directContent;
            }
        }
    }

    private String executeCalls(
            ModelResponse response,
            List<ToolDefinition> requestToolDefinitions,
            List<String> requiredToolNames,
            ToolRunContext runContext,
            List<ModelMessage> messages,
            Set<String> executedCallFingerprints,
            long runStartNanos,
            String conversationReference) throws IOException {
        Set<String> authorizedToolNameSet = requestToolDefinitions.stream()
                .map(ToolDefinition::name)
                .collect(Collectors.toUnmodifiableSet());
        for (int callIndex = 0; callIndex < response.toolCalls().size(); callIndex++) {
            ModelToolCall toolCall = response.toolCalls().get(callIndex);
            String availabilityReason = toolRegistry.availabilityReason(toolCall.name());
            if ("not_registered".equals(availabilityReason)) {
                LOG.warn("agent_unknown_tool_requested tool={}", toolCall.name());
                throw new IOException("模型请求了未知工具: " + toolCall.name());
            }
            if (!toolRegistry.available(toolCall.name())) {
                throw new IOException("模型请求了禁用工具");
            }
            if (!authorizedToolNameSet.contains(toolCall.name())) {
                LOG.warn(
                        "agent_tool_call_not_authorized requestedTool={} authorizedTools={}",
                        toolCall.name(),
                        authorizedToolNameSet);
                throw new IOException("模型请求了本轮未授权的工具");
            }
            String fingerprint = toolCall.name() + "\n" + toolCall.arguments();
            if (!executedCallFingerprints.add(fingerprint)) {
                throw new IOException("模型重复调用了相同工具和参数");
            }
            ensureWithinRunLimit(runStartNanos);
            ToolInvocation invocation = toolRegistry.execute(
                    toolCall.name(),
                    toolCall.arguments(),
                    runContext);
            ensureWithinRunLimit(runStartNanos);
            if (invocation.content().length() > properties.getMaxToolResultCharacters()) {
                throw new IOException("工具结果字符数超过限制");
            }
            if (!invocation.succeeded()) {
                if (invocation.retryable()) {
                    LOG.warn(
                            "agent_tool_retryable_failure tool={} conversation={}",
                            toolCall.name(),
                            conversationReference);
                    executedCallFingerprints.remove(fingerprint);
                    messages.add(ModelMessage.tool(
                            toolCall.id(),
                            toolCall.name(),
                            retryFeedback(toolCall, invocation),
                            List.of()));
                    return null;
                }
                return invocation.returnDirect()
                        ? invocation.content()
                        : "工具执行失败，请稍后再试。";
            }
            boolean requiredToolPending = nextRequiredTool(
                    requiredToolNames,
                    runContext).isPresent();
            boolean laterCallInSameResponse = callIndex + 1 < response.toolCalls().size();
            List<MediaPart> modelMedia = runContext.latestImageForModel(
                    toolCall.name(),
                    properties.getMaxMediaBytes());
            if (invocation.returnDirect()
                    && !requiredToolPending
                    && !laterCallInSameResponse
                    && modelMedia.isEmpty()) {
                return invocation.content();
            }
            messages.add(ModelMessage.tool(
                    toolCall.id(),
                    toolCall.name(),
                    invocation.content(),
                    modelMedia));
        }
        return null;
    }

    private void ensureWithinRunLimit(long runStartNanos) throws IOException {
        long elapsedNanos = nanoTime.getAsLong() - runStartNanos;
        if (elapsedNanos >= properties.getMaxRunDuration().toNanos()) {
            throw new IOException("Agent 单次运行时间超过限制");
        }
    }

    private static List<String> normalizeRequiredTools(
            List<String> requiredToolNames,
            List<ToolDefinition> toolDefinitions) {
        List<String> normalizedNames = requiredToolNames == null
                ? List.of()
                : requiredToolNames.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::strip)
                        .distinct()
                        .toList();
        Set<String> definedToolNameSet = toolDefinitions.stream()
                .map(ToolDefinition::name)
                .collect(Collectors.toUnmodifiableSet());
        if (!definedToolNameSet.containsAll(normalizedNames)) {
            throw new IllegalArgumentException("必需工具必须包含在可用工具定义中");
        }
        return normalizedNames;
    }

    private static Optional<String> nextRequiredTool(
            List<String> requiredToolNames,
            ToolRunContext runContext) {
        return requiredToolNames.stream()
                .filter(name -> !runContext.toolSucceeded(name))
                .findFirst();
    }

    private static String retryFeedback(
            ModelToolCall toolCall,
            ToolInvocation invocation) {
        String reason = abbreviate(invocation.content(), 320);
        if (GENERATE_DOCUMENT.equals(toolCall.name())) {
            return "文档生成工具调用失败，未执行。原因：" + reason
                    + " 请重新调用 generate_document：只发送一个合法 JSON 对象，"
                    + "不要代码块或说明文字；优先使用 content，content 与 sectionsJson 互斥，"
                    + "换行写成 \\n。例如：{\"fileName\":\"报告.docx\","
                    + "\"title\":\"标题\",\"content\":\"第一段\\n第二段\"}。";
        }
        return "工具调用失败，未执行。原因：" + reason
                + " 请严格按照本轮工具 Schema 重新调用，参数必须是单个合法 JSON 对象，"
                + "不要附加 Markdown 或解释文字。";
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "…";
    }

    private static List<ToolDefinition> definitionsForRound(
            List<ToolDefinition> toolDefinitions,
            Optional<String> nextRequiredTool) {
        if (nextRequiredTool.isEmpty()) {
            return toolDefinitions;
        }
        String requiredToolName = nextRequiredTool.get();
        return toolDefinitions.stream()
                .filter(definition -> requiredToolName.equals(definition.name()))
                .toList();
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
