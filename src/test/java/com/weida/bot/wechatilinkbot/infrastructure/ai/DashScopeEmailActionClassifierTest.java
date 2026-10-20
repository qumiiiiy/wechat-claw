package com.weida.bot.wechatilinkbot.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.agent.AgentModelClient;
import com.weida.bot.wechatilinkbot.agent.ModelMessage;
import com.weida.bot.wechatilinkbot.agent.ModelRequest;
import com.weida.bot.wechatilinkbot.agent.ModelResponse;
import com.weida.bot.wechatilinkbot.agent.ModelToolCall;
import com.weida.bot.wechatilinkbot.capability.EmailActionClassifierPort;
import com.weida.bot.wechatilinkbot.capability.EmailClassificationUnavailableException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashScopeEmailActionClassifierTest {

    private ExecutorService executor;
    private EmailClassificationProperties properties;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        properties = new EmailClassificationProperties();
        properties.setEnabled(true);
        properties.setRequestTimeout(Duration.ofSeconds(1));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void classifiesBatchThroughRequiredModelToolCall() {
        AtomicReference<ModelRequest> requestReference = new AtomicReference<>();
        DashScopeEmailActionClassifier classifier = classifier(modelClient(request -> {
            requestReference.set(request);
            return toolResponse("""
                    {"decisions":[{"index":0,"actionable":true},{"index":2,"actionable":true}]}
                    """);
        }));

        List<EmailActionClassifierPort.Decision> decisions =
                classifier.classify(List.of("请本周五前提交材料", "验证码 123456", "8月10日前交作业"));

        assertThat(decisions).extracting(EmailActionClassifierPort.Decision::actionable)
                .containsExactly(true, false, true);
        assertThat(requestReference.get().toolRequired()).isTrue();
        assertThat(requestReference.get().tools())
                .extracting(definition -> definition.name())
                .containsExactly("classify_email_actions");
        assertThat(requestReference.get().messages())
                .extracting(ModelMessage::role)
                .containsExactly(
                        com.weida.bot.wechatilinkbot.agent.ModelRole.SYSTEM,
                        com.weida.bot.wechatilinkbot.agent.ModelRole.USER);
    }

    @Test
    void defaultsMissingDecisionsToNotActionable() {
        DashScopeEmailActionClassifier classifier = classifier(modelClient(
                request -> toolResponse("{\"decisions\":[{\"index\":1,\"actionable\":true}]}")));

        List<EmailActionClassifierPort.Decision> decisions =
                classifier.classify(List.of("a", "b", "c"));

        assertThat(decisions).extracting(EmailActionClassifierPort.Decision::actionable)
                .containsExactly(false, true, false);
    }

    @Test
    void returnsEmptyWithoutCallingTheModelForNoSubjects() {
        DashScopeEmailActionClassifier classifier = classifier(modelClient(request -> {
            throw new AssertionError("model must not be called");
        }));

        assertThat(classifier.classify(List.of())).isEmpty();
    }

    @Test
    void rejectsInvalidJsonWrongToolAndOutOfRangeIndices() {
        DashScopeEmailActionClassifier invalidJson = classifier(modelClient(
                request -> toolResponse("{")));
        DashScopeEmailActionClassifier wrongTool = classifier(modelClient(
                request -> new ModelResponse(
                        "",
                        List.of(new ModelToolCall("call-1", "other_tool", "{}")))));
        DashScopeEmailActionClassifier outOfRange = classifier(modelClient(
                request -> toolResponse("{\"decisions\":[{\"index\":9,\"actionable\":true}]}")));
        DashScopeEmailActionClassifier duplicate = classifier(modelClient(
                request -> toolResponse("""
                        {"decisions":[{"index":0,"actionable":true},{"index":0,"actionable":true}]}
                        """)));

        assertThatThrownBy(() -> invalidJson.classify(List.of("a")))
                .isInstanceOf(EmailClassificationUnavailableException.class);
        assertThatThrownBy(() -> wrongTool.classify(List.of("a")))
                .isInstanceOf(EmailClassificationUnavailableException.class);
        assertThatThrownBy(() -> outOfRange.classify(List.of("a")))
                .isInstanceOf(EmailClassificationUnavailableException.class);
        assertThatThrownBy(() -> duplicate.classify(List.of("a")))
                .isInstanceOf(EmailClassificationUnavailableException.class);
    }

    @Test
    void rejectsWhenDisabledOrModelUnavailable() {
        properties.setEnabled(false);
        DashScopeEmailActionClassifier disabled = classifier(modelClient(request -> {
            throw new AssertionError("model must not be called");
        }));
        DashScopeEmailActionClassifier unavailable = new DashScopeEmailActionClassifier(
                new AgentModelClient() {
                    @Override
                    public boolean available() {
                        return false;
                    }

                    @Override
                    public ModelResponse complete(ModelRequest request) {
                        throw new UnsupportedOperationException();
                    }
                },
                properties,
                new ObjectMapper(),
                executor);

        assertThat(disabled.available()).isFalse();
        assertThat(unavailable.available()).isFalse();
        assertThatThrownBy(() -> disabled.classify(List.of("a")))
                .isInstanceOf(EmailClassificationUnavailableException.class);
    }

    @Test
    void timesOutSlowModelCalls() {
        properties.setRequestTimeout(Duration.ofMillis(20));
        DashScopeEmailActionClassifier classifier = classifier(modelClient(request -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            return toolResponse("{\"decisions\":[]}");
        }));

        assertThatThrownBy(() -> classifier.classify(List.of("a")))
                .isInstanceOf(EmailClassificationUnavailableException.class);
    }

    private DashScopeEmailActionClassifier classifier(AgentModelClient modelClient) {
        return new DashScopeEmailActionClassifier(
                modelClient,
                properties,
                new ObjectMapper(),
                executor);
    }

    private static ModelResponse toolResponse(String arguments) {
        return new ModelResponse(
                "",
                List.of(new ModelToolCall(
                        "call-1",
                        "classify_email_actions",
                        arguments)));
    }

    private static AgentModelClient modelClient(Completion completion) {
        return new AgentModelClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public ModelResponse complete(ModelRequest request) throws IOException {
                try {
                    return completion.complete(request);
                } catch (RuntimeException exception) {
                    throw new IOException("synthetic model failure", exception);
                }
            }
        };
    }

    @FunctionalInterface
    private interface Completion {

        ModelResponse complete(ModelRequest request);
    }
}
