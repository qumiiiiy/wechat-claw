package com.weida.bot.wechatilinkbot.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.model.OcsAnswerCandidate;
import com.weida.bot.wechatilinkbot.model.OcsQuestionOption;
import com.weida.bot.wechatilinkbot.model.OcsQuestionSnapshot;
import com.weida.bot.wechatilinkbot.model.OcsQuestionSnapshot.QuestionType;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OcsQuestionAnswererTest {

    private ExecutorService executor;
    private OcsAnswerProperties properties;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        properties = new OcsAnswerProperties();
        properties.setEnabled(true);
        properties.setMinimumConfidence(0.7);
        properties.setRequestTimeout(Duration.ofSeconds(1));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void obtainsValidatedChoiceAnswerThroughRequiredModelToolCall() {
        AtomicReference<ModelRequest> requestReference = new AtomicReference<>();
        AgentModelClient modelClient = modelClient(request -> {
            requestReference.set(request);
            return toolResponse("""
                    {
                      "selectedOptionIds":["B"],
                      "textAnswer":"",
                      "rationale":"合成依据",
                      "confidence":0.92
                    }
                    """);
        });
        OcsQuestionAnswerer answerer = answerer(modelClient);

        OcsAnswerCandidate candidate = answerer.answer(choiceQuestion());

        assertThat(candidate.selectedOptionIds()).containsExactly("B");
        assertThat(candidate.confidence()).isEqualTo(0.92);
        assertThat(candidate.source()).isEqualTo("agent-model");
        assertThat(requestReference.get().toolRequired()).isTrue();
        assertThat(requestReference.get().tools())
                .extracting(definition -> definition.name())
                .containsExactly("submit_ocs_answer_candidate");
    }

    @Test
    void rejectsOptionThatWasNotPresentInTheQuestion() {
        OcsQuestionAnswerer answerer = answerer(modelClient(request -> toolResponse("""
                {
                  "selectedOptionIds":["Z"],
                  "textAnswer":"",
                  "rationale":"合成依据",
                  "confidence":0.99
                }
                """)));

        assertThatThrownBy(() -> answerer.answer(choiceQuestion()))
                .isInstanceOf(OcsQuestionAnswerException.class)
                .extracting(exception -> ((OcsQuestionAnswerException) exception).reason())
                .isEqualTo(OcsQuestionAnswerException.Reason.INVALID_RESPONSE);
    }

    @Test
    void rejectsLowConfidenceAndNaturalLanguageOnlyResponses() {
        OcsQuestionAnswerer lowConfidence = answerer(modelClient(request -> toolResponse("""
                {
                  "selectedOptionIds":["A"],
                  "textAnswer":"",
                  "rationale":"不确定",
                  "confidence":0.2
                }
                """)));
        OcsQuestionAnswerer naturalLanguage = answerer(modelClient(
                request -> new ModelResponse("答案是 A", List.of())));

        assertThatThrownBy(() -> lowConfidence.answer(choiceQuestion()))
                .isInstanceOf(OcsQuestionAnswerException.class)
                .extracting(exception -> ((OcsQuestionAnswerException) exception).reason())
                .isEqualTo(OcsQuestionAnswerException.Reason.LOW_CONFIDENCE);
        assertThatThrownBy(() -> naturalLanguage.answer(choiceQuestion()))
                .isInstanceOf(OcsQuestionAnswerException.class)
                .extracting(exception -> ((OcsQuestionAnswerException) exception).reason())
                .isEqualTo(OcsQuestionAnswerException.Reason.INVALID_RESPONSE);
    }

    @Test
    void acceptsMultipleChoiceTrueFalseAndFillBlankAnswers() {
        OcsQuestionAnswerer multipleChoice = answerer(modelClient(request -> toolResponse("""
                {
                  "selectedOptionIds":["A","C"],
                  "textAnswer":"",
                  "rationale":"合成依据",
                  "confidence":0.9
                }
                """)));
        OcsQuestionAnswerer trueFalse = answerer(modelClient(request -> toolResponse("""
                {
                  "selectedOptionIds":["T"],
                  "textAnswer":"",
                  "rationale":"合成依据",
                  "confidence":0.9
                }
                """)));
        OcsQuestionAnswerer fillBlank = answerer(modelClient(request -> toolResponse("""
                {
                  "selectedOptionIds":[],
                  "textAnswer":"合成填空答案",
                  "rationale":"合成依据",
                  "confidence":0.9
                }
                """)));

        assertThat(multipleChoice.answer(question(
                QuestionType.MULTIPLE_CHOICE,
                List.of(option("A"), option("B"), option("C")))).selectedOptionIds())
                .containsExactly("A", "C");
        assertThat(trueFalse.answer(question(
                QuestionType.TRUE_FALSE,
                List.of(option("T"), option("F")))).selectedOptionIds())
                .containsExactly("T");
        assertThat(fillBlank.answer(question(QuestionType.FILL_BLANK, List.of())).textAnswer())
                .isEqualTo("合成填空答案");
    }

    @Test
    void rejectsInvalidJsonAndMapsProviderFailure() {
        OcsQuestionAnswerer invalidJson = answerer(modelClient(request -> toolResponse("{")));
        OcsQuestionAnswerer providerFailure = answerer(modelClient(request -> {
            throw new IllegalStateException("synthetic provider failure");
        }));

        assertThatThrownBy(() -> invalidJson.answer(choiceQuestion()))
                .isInstanceOf(OcsQuestionAnswerException.class)
                .extracting(exception -> ((OcsQuestionAnswerException) exception).reason())
                .isEqualTo(OcsQuestionAnswerException.Reason.INVALID_RESPONSE);
        assertThatThrownBy(() -> providerFailure.answer(choiceQuestion()))
                .isInstanceOf(OcsQuestionAnswerException.class)
                .extracting(exception -> ((OcsQuestionAnswerException) exception).reason())
                .isEqualTo(OcsQuestionAnswerException.Reason.PROVIDER_FAILURE);
    }

    @Test
    void rejectsWhenTheBoundedAnswerExecutorIsFull() throws Exception {
        executor.shutdownNow();
        executor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.submit(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        OcsQuestionAnswerer answerer = answerer(modelClient(request -> toolResponse("""
                {
                  "selectedOptionIds":["A"],
                  "textAnswer":"",
                  "rationale":"合成依据",
                  "confidence":0.9
                }
                """)));

        assertThatThrownBy(() -> answerer.answer(choiceQuestion()))
                .isInstanceOf(OcsQuestionAnswerException.class)
                .extracting(exception -> ((OcsQuestionAnswerException) exception).reason())
                .isEqualTo(OcsQuestionAnswerException.Reason.OVERLOADED);
        release.countDown();
    }

    @Test
    void timesOutSlowOnlineAnswerWithoutReturningACandidate() {
        properties.setRequestTimeout(Duration.ofMillis(20));
        OcsQuestionAnswerer answerer = answerer(modelClient(request -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            return toolResponse("""
                    {
                      "selectedOptionIds":["A"],
                      "textAnswer":"",
                      "rationale":"迟到答案",
                      "confidence":0.9
                    }
                    """);
        }));

        assertThatThrownBy(() -> answerer.answer(choiceQuestion()))
                .isInstanceOf(OcsQuestionAnswerException.class)
                .extracting(exception -> ((OcsQuestionAnswerException) exception).reason())
                .isEqualTo(OcsQuestionAnswerException.Reason.TIMEOUT);
    }

    @Test
    void rejectsUnknownQuestionTypeBeforeCallingTheModel() {
        OcsQuestionAnswerer answerer = answerer(modelClient(request -> {
            throw new AssertionError("model must not be called");
        }));
        OcsQuestionSnapshot unknown = new OcsQuestionSnapshot(
                "question-unknown",
                QuestionType.UNKNOWN,
                "合成未知题型",
                List.of(),
                "",
                "",
                Instant.parse("2026-08-03T00:00:00Z"));

        assertThatThrownBy(() -> answerer.answer(unknown))
                .isInstanceOf(OcsQuestionAnswerException.class)
                .extracting(exception -> ((OcsQuestionAnswerException) exception).reason())
                .isEqualTo(OcsQuestionAnswerException.Reason.UNSUPPORTED);
    }

    private OcsQuestionAnswerer answerer(AgentModelClient modelClient) {
        return new OcsQuestionAnswerer(
                modelClient,
                properties,
                new ObjectMapper().findAndRegisterModules(),
                executor);
    }

    private static OcsQuestionSnapshot choiceQuestion() {
        return question(
                QuestionType.SINGLE_CHOICE,
                List.of(option("A"), option("B")));
    }

    private static OcsQuestionSnapshot question(
            QuestionType type,
            List<OcsQuestionOption> options) {
        return new OcsQuestionSnapshot(
                "question-1",
                type,
                "完全合成的课程互动题",
                options,
                "第一章",
                "",
                Instant.parse("2026-08-03T00:00:00Z"));
    }

    private static OcsQuestionOption option(String id) {
        return new OcsQuestionOption(id, "合成选项 " + id);
    }

    private static ModelResponse toolResponse(String arguments) {
        return new ModelResponse(
                "",
                List.of(new ModelToolCall(
                        "call-1",
                        "submit_ocs_answer_candidate",
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
