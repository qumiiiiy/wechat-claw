package com.weida.bot.wechatilinkbot.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.weida.bot.wechatilinkbot.agent.ModelMessage;
import com.weida.bot.wechatilinkbot.agent.ModelRequest;
import com.weida.bot.wechatilinkbot.agent.ModelResponse;
import com.weida.bot.wechatilinkbot.agent.ModelToolCall;
import com.weida.bot.wechatilinkbot.model.MediaKind;
import com.weida.bot.wechatilinkbot.model.MediaPart;
import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DashScopeModelClientTest {

    @Test
    void exposesConfiguredModelAsANonSecretAuditIdentifier() {
        DashScopeModelProperties properties = properties();
        properties.setModel("synthetic-model");
        DashScopeModelClient client = new DashScopeModelClient(
                properties,
                request -> response(textMessage("合成响应")));

        assertThat(client.modelIdentifier()).isEqualTo("dashscope:synthetic-model");
    }

    @Test
    void convertsVideoAndToolDefinitionWithoutLeakingSdkTypesIntoAgent() throws Exception {
        AtomicReference<MultiModalConversationParam> captured = new AtomicReference<>();
        DashScopeModelClient client = new DashScopeModelClient(
                properties(),
                request -> {
                    captured.set(request);
                    return response(textMessage("已分析"));
                });
        ModelRequest request = new ModelRequest(
                List.of(ModelMessage.user(
                        "分析视频",
                        List.of(new MediaPart(
                                MediaKind.VIDEO,
                                "video/mp4",
                                new byte[] {0x00, 0x01, 0x02})))),
                List.of(new ToolDefinition(
                        "echo",
                        "返回输入",
                        "{\"type\":\"object\",\"properties\":{}}",
                        false)),
                true);

        ModelResponse result = client.complete(request);

        assertThat(result.text()).isEqualTo("已分析");
        assertThat(captured.get().getToolChoice()).isEqualTo("required");
        assertThat(captured.get().getTools()).hasSize(1);
        MultiModalMessage userMessage =
                (MultiModalMessage) captured.get().getMessages().getFirst();
        assertThat(userMessage.getContent().getFirst())
                .containsEntry("video", "data:video/mp4;base64,AAEC")
                .containsEntry("fps", 1.0);
    }

    @Test
    void mapsNativeFunctionCallsToProviderNeutralResponse() throws Exception {
        DashScopeModelClient client = new DashScopeModelClient(
                properties(),
                request -> response(toolCallMessage(
                        "call-1",
                        "get_current_weather",
                        "{\"location\":\"杭州\"}")));

        ModelResponse result = client.complete(new ModelRequest(
                List.of(ModelMessage.user("杭州天气", List.of())),
                List.of(),
                false));

        assertThat(result.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call-1");
            assertThat(call.name()).isEqualTo("get_current_weather");
            assertThat(call.arguments()).contains("杭州");
        });
    }

    @Test
    void forwardsGeneratedImageOnToolResultToTheMultimodalProvider() throws Exception {
        AtomicReference<MultiModalConversationParam> captured = new AtomicReference<>();
        DashScopeModelClient client = new DashScopeModelClient(
                properties(),
                request -> {
                    captured.set(request);
                    return response(textMessage("图片已看到"));
                });

        client.complete(new ModelRequest(
                List.of(
                        ModelMessage.assistantToolCalls(
                                "",
                                List.of(new ModelToolCall("call-1", "generate_image", "{}"))),
                        ModelMessage.tool(
                                "call-1",
                                "generate_image",
                                "{\"status\":\"ok\"}",
                                List.of(new MediaPart(
                                        MediaKind.IMAGE,
                                        "image/png",
                                        new byte[] {0x01, 0x02})))),
                List.of(),
                false));

        MultiModalMessage toolMessage =
                (MultiModalMessage) captured.get().getMessages().getLast();
        assertThat(toolMessage.getRole()).isEqualTo("tool");
        assertThat(toolMessage.getContent().get(1))
                .containsEntry("image", "data:image/png;base64,AQI=");
    }

    @Test
    void retriesOnlyInsideTheProviderAdapter() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        DashScopeModelClient client = new DashScopeModelClient(
                properties(),
                request -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new IllegalStateException("temporary provider failure");
                    }
                    return response(textMessage("重试成功"));
                });

        ModelResponse result = client.complete(new ModelRequest(
                List.of(ModelMessage.user("你好", List.of())),
                List.of(),
                false));

        assertThat(result.text()).isEqualTo("重试成功");
        assertThat(attempts).hasValue(3);
    }

    private static DashScopeModelProperties properties() {
        DashScopeModelProperties properties = new DashScopeModelProperties();
        properties.setApiKey("test-key");
        properties.setMaxAttempts(3);
        return properties;
    }

    private static MultiModalMessage textMessage(String text) {
        return MultiModalMessage.builder()
                .role("assistant")
                .content(List.of(Map.of("text", text)))
                .build();
    }

    private static MultiModalMessage toolCallMessage(
            String callId,
            String toolName,
            String arguments) {
        ToolCallFunction toolCall = new ToolCallFunction();
        toolCall.setId(callId);
        toolCall.setType("function");
        ToolCallFunction.CallFunction function = toolCall.new CallFunction();
        function.setName(toolName);
        function.setArguments(arguments);
        toolCall.setFunction(function);
        return MultiModalMessage.builder()
                .role("assistant")
                .content(List.of())
                .toolCalls(List.of(toolCall))
                .build();
    }

    private static MultiModalConversationResult response(MultiModalMessage message) {
        MultiModalConversationOutput.Choice choice =
                new MultiModalConversationOutput.Choice();
        choice.setMessage(message);
        MultiModalConversationOutput output = new MultiModalConversationOutput();
        output.setChoices(List.of(choice));
        MultiModalConversationResult result = mock(MultiModalConversationResult.class);
        when(result.getOutput()).thenReturn(output);
        return result;
    }
}
