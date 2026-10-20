package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.DesktopTaskPolicyViolationException;
import com.weida.bot.wechatilinkbot.capability.EmailDeliveryException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ToolRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void derivesProviderNeutralSchemaAndExecutesTypedInput() {
        ToolRegistry registry = new ToolRegistry(List.of(echoTool()), objectMapper);

        ToolDefinition definition = registry.listDefinitions(true, Set.of()).getFirst();
        ToolInvocation result = registry.execute(
                "echo",
                "{\"value\":\"hello\"}",
                runContext());

        assertThat(definition.name()).isEqualTo("echo");
        assertThat(definition.inputSchema())
                .contains("\"value\"")
                .contains("\"required\"");
        assertThat(result.content()).isEqualTo("hello");
    }

    @Test
    void hidesExternalDeliveryDetailsFromTheModelAndLogs(CapturedOutput output) {
        ToolRegistry registry = new ToolRegistry(List.of(failingEmailTool()), objectMapper);

        ToolInvocation result = registry.execute(
                "send_email",
                "{\"value\":\"confirm\"}",
                runContext());

        assertThat(result.content())
                .contains("请先检查 QQ 已发送邮件")
                .doesNotContain("sensitive-upstream-response");
        assertThat(output).doesNotContain("sensitive-upstream-response");
    }

    @Test
    void returnsDesktopPolicyRejectionToTheModelWithoutLoggingAnExecutionFailure(
            CapturedOutput output) {
        ToolRegistry registry = new ToolRegistry(List.of(policyRejectedTool()), objectMapper);

        ToolInvocation result = registry.execute(
                "request_open_url",
                "{\"value\":\"https://mail.qq.com/\"}",
                runContext());

        assertThat(result.content()).isEqualTo("该网址主机不在本机允许列表中");
        assertThat(result.retryable()).isFalse();
        assertThat(output).contains("agent_tool_input_rejected")
                .doesNotContain("agent_tool_execution_failed");
    }

    @Test
    void marksMalformedJsonAsRetryableForTheAgentLoop() {
        ToolRegistry registry = new ToolRegistry(List.of(echoTool()), objectMapper);

        ToolInvocation result = registry.execute("echo", "不是 JSON", runContext());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.content())
                .isEqualTo("{\"status\":\"error\",\"message\":\"工具参数不是有效 JSON\"}");
    }

    @Test
    void acceptsJsonArgumentsWrappedInAJsonCodeFence() {
        ToolRegistry registry = new ToolRegistry(List.of(echoTool()), objectMapper);

        ToolInvocation result = registry.execute(
                "echo",
                "```json\n{\"value\":\"fenced\"}\n```",
                runContext());

        assertThat(result.succeeded()).isTrue();
        assertThat(result.content()).isEqualTo("fenced");
    }

    @Test
    void propagatesRetryableStatusReturnedByAValidationTool() {
        AgentTool<TestInput> validationTool = new TestTool("validation", true) {
            @Override
            public ToolExecution execute(TestInput input, ToolContext context) {
                return new ToolExecution(
                        "{\"status\":\"error\",\"retryable\":true}",
                        null);
            }
        };
        ToolRegistry registry = new ToolRegistry(List.of(validationTool), objectMapper);

        ToolInvocation result = registry.execute(
                "validation",
                "{\"value\":\"bad\"}",
                runContext());

        assertThat(result.succeeded()).isFalse();
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void exposesOnlyStableAvailabilityCodes() {
        ToolRegistry registry = new ToolRegistry(List.of(unavailableTool()), objectMapper);

        assertThat(registry.availabilityReason("send_email")).isEqualTo("disabled");
        assertThat(registry.availabilityReason("missing")).isEqualTo("not_registered");
    }

    @Test
    void hidesHighRiskToolsUntilAnIntentExplicitlyAuthorizesThem() {
        AgentTool<TestInput> highRiskTool = new TestTool("desktop", true) {
            @Override
            public boolean requiresExplicitIntent() {
                return true;
            }
        };
        ToolRegistry registry = new ToolRegistry(List.of(echoTool(), highRiskTool), objectMapper);

        assertThat(registry.listDefinitions(true, Set.of()))
                .extracting(ToolDefinition::name)
                .containsExactly("echo");
        assertThat(registry.listDefinitions(true, Set.of("desktop")))
                .extracting(ToolDefinition::name)
                .containsExactlyInAnyOrder("echo", "desktop");
    }

    @Test
    void exposesAvailableExplicitToolsToTheModelPlanner() {
        AgentTool<TestInput> highRiskTool = new TestTool("desktop", true) {
            @Override
            public boolean requiresExplicitIntent() {
                return true;
            }
        };
        ToolRegistry registry = new ToolRegistry(List.of(echoTool(), highRiskTool), objectMapper);

        assertThat(registry.listModelDefinitions(true))
                .extracting(ToolDefinition::name)
                .containsExactlyInAnyOrder("echo", "desktop");
    }

    private ToolRunContext runContext() {
        return new ToolRunContext(
                "wechat-ilink|user-1",
                "test",
                true,
                false,
                1,
                objectMapper);
    }

    private static AgentTool<TestInput> echoTool() {
        return new TestTool("echo", true) {
            @Override
            public ToolExecution execute(TestInput input, ToolContext context) {
                return new ToolExecution(input.value(), null);
            }
        };
    }

    private static AgentTool<TestInput> failingEmailTool() {
        return new TestTool("send_email", true) {
            @Override
            public ToolExecution execute(TestInput input, ToolContext context) {
                throw new EmailDeliveryException(
                        "safe-wrapper",
                        new IllegalStateException("sensitive-upstream-response"));
            }
        };
    }

    private static AgentTool<TestInput> unavailableTool() {
        return new TestTool("send_email", false) {
            @Override
            public String availabilityReason() {
                return "disabled";
            }
        };
    }

    private static AgentTool<TestInput> policyRejectedTool() {
        return new TestTool("request_open_url", true) {
            @Override
            public boolean returnDirect() {
                return true;
            }

            @Override
            public ToolExecution execute(TestInput input, ToolContext context) {
                throw new DesktopTaskPolicyViolationException("该网址主机不在本机允许列表中");
            }
        };
    }

    private record TestInput(
            @ToolParameter(description = "test value") String value) {
    }

    private static class TestTool implements AgentTool<TestInput> {

        private final String name;
        private final boolean available;

        private TestTool(String name, boolean available) {
            this.name = name;
            this.available = available;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "test";
        }

        @Override
        public Class<TestInput> inputType() {
            return TestInput.class;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean producesMedia() {
            return false;
        }

        @Override
        public ToolExecution execute(TestInput input, ToolContext context) {
            throw new UnsupportedOperationException("unavailable");
        }
    }
}
