package com.weida.bot.wechatilinkbot.agent;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.CURRENT_WEATHER;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DOCUMENT;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.SEND_EMAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.tool.AgentTool;
import com.weida.bot.wechatilinkbot.tool.ToolContext;
import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import com.weida.bot.wechatilinkbot.tool.ToolExecution;
import com.weida.bot.wechatilinkbot.tool.ToolParameter;
import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import com.weida.bot.wechatilinkbot.tool.ToolRunContext;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AgentToolLoopTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void executesProviderNeutralFunctionCallAndContinuesTheModelLoop() throws Exception {
        List<ModelRequest> requestList = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient modelClient = modelClient(request -> {
            requestList.add(request);
            if (calls.getAndIncrement() == 0) {
                return new ModelResponse(
                        "",
                        List.of(new ModelToolCall(
                                "call-1",
                                "echo",
                                "{\"value\":\"hello\"}")));
            }
            return new ModelResponse("工具结果已收到", List.of());
        });
        ToolRegistry registry = new ToolRegistry(List.of(echoTool(false)), objectMapper);
        AgentToolLoop loop = new AgentToolLoop(
                modelClient,
                registry,
                new AgentProperties());

        String result = loop.complete(
                List.of(ModelMessage.user("测试", List.of())),
                registry.listDefinitions(true, java.util.Set.of()),
                List.of("echo"),
                runContext(),
                "conversation");

        assertThat(result).isEqualTo("工具结果已收到");
        assertThat(requestList).hasSize(2);
        assertThat(requestList.getFirst().toolRequired()).isTrue();
        assertThat(requestList.getLast().toolRequired()).isFalse();
        assertThat(requestList.getLast().messages())
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo(ModelRole.TOOL);
                    assertThat(message.text()).isEqualTo("hello");
                });
    }

    @Test
    void retriesAFunctionCallWhenTheModelInitiallyReturnsMalformedJson() throws Exception {
        List<ModelRequest> requestList = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient modelClient = modelClient(request -> {
            requestList.add(request);
            if (calls.getAndIncrement() == 0) {
                return new ModelResponse(
                        "",
                        List.of(new ModelToolCall("bad-call", GENERATE_DOCUMENT, "不是 JSON")));
            }
            return new ModelResponse(
                    "",
                    List.of(new ModelToolCall(
                            "retry-call",
                            GENERATE_DOCUMENT,
                            "{\"value\":\"重试成功\"}")));
        });
        ToolRegistry registry = new ToolRegistry(
                List.of(fixedTool(GENERATE_DOCUMENT, true, null)),
                objectMapper);
        AgentToolLoop loop = new AgentToolLoop(modelClient, registry, new AgentProperties());

        String result = loop.complete(
                List.of(ModelMessage.user("生成文档", List.of())),
                registry.listDefinitions(true, java.util.Set.of()),
                List.of(GENERATE_DOCUMENT),
                runContext(),
                "conversation");

        assertThat(result).isEqualTo("重试成功");
        assertThat(requestList).hasSize(2);
        assertThat(requestList.get(1).toolRequired()).isTrue();
        assertThat(requestList.get(1).messages())
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo(ModelRole.TOOL);
                    assertThat(message.text())
                            .contains("文档生成工具调用失败")
                            .contains("content 与 sectionsJson 互斥")
                            .contains("合法 JSON");
                });
    }

    @Test
    void letsTheModelChooseOrderedToolsWithoutARequiredToolList() throws Exception {
        List<ModelRequest> requestList = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient modelClient = modelClient(request -> {
            requestList.add(request);
            if (calls.getAndIncrement() == 0) {
                return new ModelResponse(
                        "",
                        List.of(new ModelToolCall(
                                "weather-call",
                                CURRENT_WEATHER,
                                "{\"value\":\"杭州\"}")));
            }
            return new ModelResponse(
                    "",
                    List.of(new ModelToolCall(
                            "email-call",
                            SEND_EMAIL,
                            "{\"value\":\"prepare\"}")));
        });
        ToolRegistry registry = new ToolRegistry(
                List.of(
                        fixedTool(CURRENT_WEATHER, false, "{\"status\":\"success\"}"),
                        fixedTool(SEND_EMAIL, true, "{\"status\":\"approval_required\"}")),
                objectMapper);
        AgentToolLoop loop = new AgentToolLoop(
                modelClient,
                registry,
                new AgentProperties());

        String result = loop.complete(
                List.of(ModelMessage.user("查询杭州天气后准备邮件", List.of())),
                registry.listModelDefinitions(true),
                List.of(),
                emailRunContext(),
                "conversation");

        assertThat(result).contains("approval_required");
        assertThat(requestList).hasSize(2);
        assertThat(requestList.getFirst().toolRequired()).isFalse();
        assertThat(requestList.getFirst().tools())
                .extracting(ToolDefinition::name)
                .containsExactly(CURRENT_WEATHER, SEND_EMAIL);
        assertThat(requestList.getLast().messages())
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo(ModelRole.TOOL);
                    assertThat(message.toolName()).isEqualTo(CURRENT_WEATHER);
                });
    }

    @Test
    void returnsGeneratedImageAsModelVisibleToolMedia() throws Exception {
        List<ModelRequest> requestList = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient modelClient = modelClient(request -> {
            requestList.add(request);
            if (calls.getAndIncrement() == 0) {
                return new ModelResponse(
                        "",
                        List.of(new ModelToolCall("image-call", "image_tool", "{}")));
            }
            return new ModelResponse("图片已收到", List.of());
        });
        GeneratedMedia image = new GeneratedMedia(
                GeneratedMediaKind.IMAGE,
                new byte[] {1, 2, 3},
                "generated.png",
                0L,
                "");
        ToolRegistry registry = new ToolRegistry(
                List.of(fixedMediaTool("image_tool", true, image)),
                objectMapper);
        AgentToolLoop loop = new AgentToolLoop(
                modelClient,
                registry,
                new AgentProperties());

        String result = loop.complete(
                List.of(ModelMessage.user("分析这张图", List.of())),
                registry.listModelDefinitions(true),
                List.of(),
                runContext(),
                "conversation");

        assertThat(result).isEqualTo("图片已收到");
        assertThat(requestList).hasSize(2);
        assertThat(requestList.getLast().messages())
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo(ModelRole.TOOL);
                    assertThat(message.mediaParts()).singleElement().satisfies(media -> {
                        assertThat(media.mimeType()).isEqualTo("image/png");
                        assertThat(media.bytes()).containsExactly(1, 2, 3);
                    });
                });
    }

    @Test
    void returnDirectToolStopsWithoutAnotherProviderCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient modelClient = modelClient(request -> {
            calls.incrementAndGet();
            return new ModelResponse(
                    "",
                    List.of(new ModelToolCall("call-1", "echo", "{\"value\":\"done\"}")));
        });
        ToolRegistry registry = new ToolRegistry(List.of(echoTool(true)), objectMapper);
        AgentToolLoop loop = new AgentToolLoop(
                modelClient,
                registry,
                new AgentProperties());

        String result = loop.complete(
                List.of(ModelMessage.user("测试", List.of())),
                registry.listDefinitions(true, java.util.Set.of()),
                List.of("echo"),
                runContext(),
                "conversation");

        assertThat(result).isEqualTo("done");
        assertThat(calls).hasValue(1);
    }

    @Test
    void enforcesTheConfiguredToolRoundLimit() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxToolRounds(0);
        AgentModelClient modelClient = modelClient(request -> new ModelResponse(
                "",
                List.of(new ModelToolCall("call-1", "echo", "{\"value\":\"x\"}"))));
        ToolRegistry registry = new ToolRegistry(List.of(echoTool(false)), objectMapper);
        AgentToolLoop loop = new AgentToolLoop(modelClient, registry, properties);

        assertThatThrownBy(() -> loop.complete(
                List.of(ModelMessage.user("测试", List.of())),
                registry.listDefinitions(true, java.util.Set.of()),
                List.of("echo"),
                runContext(),
                "conversation"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("超过限制");
    }

    @Test
    void executesWeatherThenEmailAsAnOrderedRequiredPlan() throws Exception {
        List<ModelRequest> requestList = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient modelClient = modelClient(request -> {
            requestList.add(request);
            if (calls.getAndIncrement() == 0) {
                return new ModelResponse(
                        "",
                        List.of(new ModelToolCall(
                                "weather-call",
                                CURRENT_WEATHER,
                                "{\"value\":\"杭州\"}")));
            }
            return new ModelResponse(
                    "",
                    List.of(new ModelToolCall(
                            "email-call",
                            SEND_EMAIL,
                            "{\"value\":\"prepare\"}")));
        });
        ToolRegistry registry = new ToolRegistry(
                List.of(
                        fixedTool(
                                CURRENT_WEATHER,
                                false,
                                "{\"status\":\"success\"}"),
                        fixedTool(
                                SEND_EMAIL,
                                true,
                                "{\"status\":\"approval_required\"}")),
                objectMapper);
        AgentToolLoop loop = new AgentToolLoop(
                modelClient,
                registry,
                new AgentProperties());

        String result = loop.complete(
                List.of(ModelMessage.user("查询杭州天气后准备邮件", List.of())),
                registry.listDefinitions(true, java.util.Set.of()),
                List.of(CURRENT_WEATHER, SEND_EMAIL),
                emailRunContext(),
                "conversation");

        assertThat(result).contains("approval_required");
        assertThat(requestList).hasSize(2);
        assertThat(requestList.getFirst().toolRequired()).isTrue();
        assertThat(requestList.getFirst().tools())
                .extracting(ToolDefinition::name)
                .containsExactly(CURRENT_WEATHER);
        assertThat(requestList.getLast().toolRequired()).isTrue();
        assertThat(requestList.getLast().tools())
                .extracting(ToolDefinition::name)
                .containsExactly(SEND_EMAIL);
        assertThat(requestList.getLast().messages())
                .anySatisfy(message -> {
                    assertThat(message.role()).isEqualTo(ModelRole.TOOL);
                    assertThat(message.toolName()).isEqualTo(CURRENT_WEATHER);
                });
    }

    @Test
    void rejectsAFunctionThatWasNotAuthorizedForTheCurrentStage() {
        AgentModelClient modelClient = modelClient(request -> new ModelResponse(
                "",
                List.of(new ModelToolCall(
                        "email-call",
                        SEND_EMAIL,
                        "{\"value\":\"prepare\"}"))));
        ToolRegistry registry = new ToolRegistry(
                List.of(
                        fixedTool(
                                CURRENT_WEATHER,
                                false,
                                "{\"status\":\"success\"}"),
                        fixedTool(
                                SEND_EMAIL,
                                true,
                                "{\"status\":\"approval_required\"}")),
                objectMapper);
        AgentToolLoop loop = new AgentToolLoop(
                modelClient,
                registry,
                new AgentProperties());

        assertThatThrownBy(() -> loop.complete(
                List.of(ModelMessage.user("测试", List.of())),
                registry.listDefinitions(true, java.util.Set.of()),
                List.of(CURRENT_WEATHER, SEND_EMAIL),
                emailRunContext(),
                "conversation"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("未授权");
    }

    @Test
    void rejectsUnknownToolCalls() {
        AgentModelClient modelClient = modelClient(request -> new ModelResponse(
                "",
                List.of(new ModelToolCall("call-1", "missing", "{}"))));
        ToolRegistry registry = new ToolRegistry(List.of(echoTool(false)), objectMapper);
        AgentToolLoop loop = new AgentToolLoop(modelClient, registry, new AgentProperties());

        assertThatThrownBy(() -> loop.complete(
                List.of(ModelMessage.user("测试", List.of())),
                registry.listDefinitions(true, java.util.Set.of()),
                List.of(),
                runContext(),
                "conversation"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("未知工具");
    }

    @Test
    void rejectsDisabledToolCalls() {
        AgentTool<EchoInput> disabled = disabledTool("disabled");
        ToolRegistry registry = new ToolRegistry(List.of(disabled), objectMapper);
        AgentModelClient modelClient = modelClient(request -> new ModelResponse(
                "",
                List.of(new ModelToolCall("call-1", "disabled", "{}"))));
        AgentToolLoop loop = new AgentToolLoop(modelClient, registry, new AgentProperties());
        ToolDefinition definition = new ToolDefinition(
                "disabled", "disabled tool", "{\"type\":\"object\"}", false);

        assertThatThrownBy(() -> loop.complete(
                List.of(ModelMessage.user("测试", List.of())),
                List.of(definition),
                List.of(),
                runContext(),
                "conversation"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("禁用工具");
    }

    @Test
    void rejectsRepeatedToolAndArgumentsAcrossRounds() {
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient modelClient = modelClient(request -> {
            calls.incrementAndGet();
            return new ModelResponse(
                    "",
                    List.of(new ModelToolCall("call-" + calls.get(), "echo", "{\"value\":\"x\"}")));
        });
        ToolRegistry registry = new ToolRegistry(List.of(echoTool(false)), objectMapper);
        AgentToolLoop loop = new AgentToolLoop(modelClient, registry, new AgentProperties());

        assertThatThrownBy(() -> loop.complete(
                List.of(ModelMessage.user("测试", List.of())),
                registry.listDefinitions(true, java.util.Set.of()),
                List.of("echo"),
                runContext(),
                "conversation"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("重复调用");
        assertThat(calls).hasValue(2);
    }

    @Test
    void rejectsToolResultsOverTheCharacterLimit() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxToolResultCharacters(256);
        AgentModelClient modelClient = modelClient(request -> new ModelResponse(
                "",
                List.of(new ModelToolCall("call-1", "echo", "{\"value\":\"x\"}"))));
        ToolRegistry registry = new ToolRegistry(
                List.of(fixedTool("echo", true, "x".repeat(257))),
                objectMapper);
        AgentToolLoop loop = new AgentToolLoop(modelClient, registry, properties);

        assertThatThrownBy(() -> loop.complete(
                List.of(ModelMessage.user("测试", List.of())),
                registry.listDefinitions(true, java.util.Set.of()),
                List.of("echo"),
                runContext(),
                "conversation"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("结果字符数超过限制");
    }

    @Test
    void toolExceptionStopsBeforeModelCanClaimSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient modelClient = modelClient(request -> {
            if (calls.getAndIncrement() == 0) {
                return new ModelResponse(
                        "",
                        List.of(new ModelToolCall("call-1", "failure", "{\"value\":\"x\"}")));
            }
            return new ModelResponse("已经成功", List.of());
        });
        ToolRegistry registry = new ToolRegistry(List.of(failingTool()), objectMapper);
        AgentToolLoop loop = new AgentToolLoop(modelClient, registry, new AgentProperties());

        String result = loop.complete(
                List.of(ModelMessage.user("测试", List.of())),
                registry.listDefinitions(true, java.util.Set.of()),
                List.of("failure"),
                runContext(),
                "conversation");

        assertThat(result).isEqualTo("工具执行失败，请稍后再试。");
        assertThat(calls).hasValue(1);
    }

    @Test
    void enforcesTotalRunDurationAfterModelBoundary() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxRunDuration(Duration.ofNanos(10));
        AtomicLong now = new AtomicLong();
        AgentModelClient modelClient = modelClient(request -> {
            now.set(11);
            return new ModelResponse("迟到的结果", List.of());
        });
        ToolRegistry registry = new ToolRegistry(List.of(), objectMapper);
        AgentToolLoop loop = new AgentToolLoop(modelClient, registry, properties, now::get);

        assertThatThrownBy(() -> loop.complete(
                List.of(ModelMessage.user("测试", List.of())),
                List.of(),
                List.of(),
                runContext(),
                "conversation"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("运行时间超过限制");
    }

    private ToolRunContext runContext() {
        return new ToolRunContext(
                "conversation",
                "测试",
                false,
                false,
                1,
                objectMapper);
    }

    private ToolRunContext emailRunContext() {
        return new ToolRunContext(
                "conversation",
                "查询杭州天气后准备邮件",
                true,
                true,
                1,
                objectMapper);
    }

    private static AgentModelClient modelClient(Completion completion) {
        return new AgentModelClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public ModelResponse complete(ModelRequest request) throws IOException {
                return completion.complete(request);
            }
        };
    }

    private static AgentTool<EchoInput> echoTool(boolean returnDirect) {
        return fixedTool("echo", returnDirect, null);
    }

    private static AgentTool<EchoInput> fixedTool(
            String name,
            boolean returnDirect,
            String fixedResult) {
        return new AgentTool<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "返回输入";
            }

            @Override
            public Class<EchoInput> inputType() {
                return EchoInput.class;
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean producesMedia() {
                return false;
            }

            @Override
            public boolean returnDirect() {
                return returnDirect;
            }

            @Override
            public ToolExecution execute(EchoInput input, ToolContext context) {
                String result = fixedResult == null ? input.value() : fixedResult;
                return new ToolExecution(result, null);
            }
        };
    }

    private static AgentTool<EchoInput> fixedMediaTool(
            String name,
            boolean returnDirect,
            GeneratedMedia media) {
        return new AgentTool<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "返回媒体";
            }

            @Override
            public Class<EchoInput> inputType() {
                return EchoInput.class;
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean producesMedia() {
                return true;
            }

            @Override
            public boolean returnDirect() {
                return returnDirect;
            }

            @Override
            public ToolExecution execute(EchoInput input, ToolContext context) {
                return new ToolExecution("{\"status\":\"ok\"}", media);
            }
        };
    }

    private static AgentTool<EchoInput> disabledTool(String name) {
        return new AgentTool<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "disabled";
            }

            @Override
            public Class<EchoInput> inputType() {
                return EchoInput.class;
            }

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public boolean producesMedia() {
                return false;
            }

            @Override
            public ToolExecution execute(EchoInput input, ToolContext context) {
                throw new AssertionError("disabled tool must not execute");
            }
        };
    }

    private static AgentTool<EchoInput> failingTool() {
        return new AgentTool<>() {
            @Override
            public String name() {
                return "failure";
            }

            @Override
            public String description() {
                return "fails";
            }

            @Override
            public Class<EchoInput> inputType() {
                return EchoInput.class;
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean producesMedia() {
                return false;
            }

            @Override
            public ToolExecution execute(EchoInput input, ToolContext context) {
                throw new IllegalStateException("sensitive upstream detail");
            }
        };
    }

    private record EchoInput(
            @ToolParameter(description = "echo value") String value) {
    }

    @FunctionalInterface
    private interface Completion {

        ModelResponse complete(ModelRequest request) throws IOException;
    }
}
