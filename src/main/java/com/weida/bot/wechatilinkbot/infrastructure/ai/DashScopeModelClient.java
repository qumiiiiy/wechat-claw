package com.weida.bot.wechatilinkbot.infrastructure.ai;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.protocol.ConnectionOptions;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolBase;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolFunction;
import com.google.gson.JsonParser;
import com.weida.bot.wechatilinkbot.agent.AgentModelClient;
import com.weida.bot.wechatilinkbot.agent.ModelMessage;
import com.weida.bot.wechatilinkbot.agent.ModelRequest;
import com.weida.bot.wechatilinkbot.agent.ModelResponse;
import com.weida.bot.wechatilinkbot.agent.ModelToolCall;
import com.weida.bot.wechatilinkbot.model.MediaPart;
import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The only AI SDK adapter in the Bot.
 *
 * <p>It converts stable internal messages to DashScope types and performs one remote
 * completion. Tool authorization and execution remain in the agent module.</p>
 */
@Component
public class DashScopeModelClient implements AgentModelClient {

    private static final Logger LOG = LoggerFactory.getLogger(DashScopeModelClient.class);
    private static final String HTTP_PROTOCOL = "http";

    private final DashScopeModelProperties properties;
    private final ProviderClient providerClient;

    @Autowired
    public DashScopeModelClient(DashScopeModelProperties properties) {
        this(properties, createProviderClient(properties));
    }

    DashScopeModelClient(
            DashScopeModelProperties properties,
            ProviderClient providerClient) {
        this.properties = properties;
        this.providerClient = providerClient;
    }

    @Override
    public boolean available() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank();
    }

    @Override
    public String modelIdentifier() {
        return "dashscope:" + properties.getModel();
    }

    @Override
    public ModelResponse complete(ModelRequest request) throws IOException {
        if (!available()) {
            throw new IOException("百炼聊天模型当前不可用");
        }
        MultiModalConversationParam nativeRequest = nativeRequest(request);
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                return response(providerClient.call(nativeRequest));
            } catch (Exception exception) {
                lastFailure = exception;
                if (exception instanceof ApiException apiEx && apiEx.getStatus() != null) {
                    LOG.warn(
                            "dashscope_model_attempt_failed model={} attempt={} "
                                    + "statusCode={} errorCode={} errorMessage={} requestId={}",
                            properties.getModel(),
                            attempt,
                            apiEx.getStatus().getStatusCode(),
                            apiEx.getStatus().getCode(),
                            apiEx.getStatus().getMessage(),
                            apiEx.getStatus().getRequestId());
                } else {
                    LOG.warn(
                            "dashscope_model_attempt_failed model={} attempt={} causeType={} message={}",
                            properties.getModel(),
                            attempt,
                            exception.getClass().getSimpleName(),
                            exception.getMessage());
                }
            }
        }
        IOException safeFailure = new IOException("百炼聊天模型请求失败");
        if (lastFailure != null) {
            safeFailure.setStackTrace(lastFailure.getStackTrace());
        }
        LOG.error(
                "dashscope_model_request_failed model={} attempts={}",
                properties.getModel(),
                properties.getMaxAttempts(),
                safeFailure);
        throw safeFailure;
    }

    private MultiModalConversationParam nativeRequest(ModelRequest request) {
        MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder =
                MultiModalConversationParam.builder()
                        .apiKey(properties.getApiKey().strip())
                        .model(properties.getModel())
                        .messages(request.messages().stream()
                                .map(this::nativeMessage)
                                .toList()).enableSearch(true)
                        .temperature((float) properties.getTemperature())
                        .maxTokens(properties.getMaxCompletionTokens())
                        .enableThinking(false)
                        .parallelToolCalls(false)
                        .enableSearch(true);
        List<ToolBase> nativeTools = request.tools().stream()
                .map(this::nativeTool)
                .map(ToolBase.class::cast)
                .toList();
        if (!nativeTools.isEmpty()) {
            builder.tools(nativeTools)
                    .toolChoice(request.toolRequired() ? "required" : "auto");
        }
        return builder.build();
    }

    private MultiModalMessage nativeMessage(ModelMessage message) {
        return switch (message.role()) {
            case SYSTEM -> textMessage("system", message.text());
            case USER -> userMessage(message);
            case ASSISTANT -> assistantMessage(message);
            case TOOL -> toolMessage(message);
        };
    }

    private MultiModalMessage userMessage(ModelMessage message) {
        List<Map<String, Object>> contentList =
                new ArrayList<>(message.mediaParts().size() + 1);
        message.mediaParts().stream()
                .map(this::mediaContent)
                .forEach(contentList::add);
        contentList.add(Map.of("text", message.text()));
        return MultiModalMessage.builder()
                .role("user")
                .content(List.copyOf(contentList))
                .build();
    }

    private MultiModalMessage assistantMessage(ModelMessage message) {
        MultiModalMessage.MultiModalMessageBuilder<?, ?> builder =
                MultiModalMessage.builder()
                        .role("assistant")
                        .content(List.of(Map.of("text", message.text())));
        if (!message.toolCalls().isEmpty()) {
            builder.toolCalls(message.toolCalls().stream()
                    .map(this::nativeToolCall)
                    .map(ToolCallBase.class::cast)
                    .toList());
        }
        return builder.build();
    }

    private MultiModalMessage toolMessage(ModelMessage message) {
        List<Map<String, Object>> contentList =
                new ArrayList<>(message.mediaParts().size() + 1);
        contentList.add(Map.of("text", message.text()));
        message.mediaParts().stream()
                .map(this::mediaContent)
                .forEach(contentList::add);
        return MultiModalMessage.builder()
                .role("tool")
                .toolCallId(message.toolCallId())
                .name(message.toolName())
                .content(List.copyOf(contentList))
                .build();
    }

    private MultiModalMessage textMessage(String role, String text) {
        return MultiModalMessage.builder()
                .role(role)
                .content(List.of(Map.of("text", text)))
                .build();
    }

    private Map<String, Object> mediaContent(MediaPart mediaPart) {
        String dataUrl = "data:"
                + mediaPart.mimeType()
                + ";base64,"
                + Base64.getEncoder().encodeToString(mediaPart.bytes());
        return switch (mediaPart.kind()) {
            case IMAGE -> Map.of("image", dataUrl);
            case VIDEO -> Map.of("video", dataUrl, "fps", 1.0);
        };
    }

    private ToolFunction nativeTool(ToolDefinition definition) {
        return ToolFunction.builder()
                .function(FunctionDefinition.builder()
                        .name(definition.name())
                        .description(definition.description())
                        .parameters(JsonParser.parseString(definition.inputSchema())
                                .getAsJsonObject())
                        .build())
                .build();
    }

    private ToolCallFunction nativeToolCall(ModelToolCall toolCall) {
        ToolCallFunction nativeCall = new ToolCallFunction();
        nativeCall.setId(toolCall.id());
        nativeCall.setType("function");
        ToolCallFunction.CallFunction function = nativeCall.new CallFunction();
        function.setName(toolCall.name());
        function.setArguments(toolCall.arguments());
        nativeCall.setFunction(function);
        return nativeCall;
    }

    private ModelResponse response(MultiModalConversationResult result) throws IOException {
        MultiModalMessage message = responseMessage(result);
        List<ModelToolCall> toolCallList = toolCalls(message);
        String text = textContent(message);
        if (text.isBlank() && toolCallList.isEmpty()) {
            throw new IOException("百炼聊天模型返回了空内容");
        }
        return new ModelResponse(text, toolCallList);
    }

    private MultiModalMessage responseMessage(
            MultiModalConversationResult result) throws IOException {
        if (result == null || result.getOutput() == null) {
            throw new IOException("百炼聊天模型返回了空响应");
        }
        MultiModalConversationOutput output = result.getOutput();
        if (output.getChoices() == null || output.getChoices().isEmpty()) {
            throw new IOException("百炼聊天模型响应中缺少 choices");
        }
        MultiModalMessage message = output.getChoices().getFirst().getMessage();
        if (message == null) {
            throw new IOException("百炼聊天模型返回了空结果");
        }
        return message;
    }

    private List<ModelToolCall> toolCalls(MultiModalMessage message) throws IOException {
        if (message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
            return List.of();
        }
        List<ModelToolCall> toolCallList = new ArrayList<>(message.getToolCalls().size());
        for (ToolCallBase toolCall : message.getToolCalls()) {
            if (!(toolCall instanceof ToolCallFunction functionCall)
                    || functionCall.getFunction() == null) {
                throw new IOException("百炼返回了不受支持的工具调用");
            }
            toolCallList.add(new ModelToolCall(
                    functionCall.getId(),
                    functionCall.getFunction().getName(),
                    functionCall.getFunction().getArguments()));
        }
        return List.copyOf(toolCallList);
    }

    private String textContent(MultiModalMessage message) {
        if (message.getContent() == null) {
            return "";
        }
        return message.getContent().stream()
                .map(item -> item.get("text"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.joining());
    }

    private static ProviderClient createProviderClient(
            DashScopeModelProperties properties) {
        ConnectionOptions options = ConnectionOptions.builder()
                .connectTimeout(properties.getConnectTimeout())
                .writeTimeout(properties.getRequestTimeout())
                .readTimeout(properties.getRequestTimeout())
                .build();
        return request -> new MultiModalConversation(
                        HTTP_PROTOCOL,
                        properties.getBaseUrl().toString(),
                        options)
                .call(request);
    }

    @FunctionalInterface
    interface ProviderClient {

        MultiModalConversationResult call(
                MultiModalConversationParam request) throws Exception;
    }
}
