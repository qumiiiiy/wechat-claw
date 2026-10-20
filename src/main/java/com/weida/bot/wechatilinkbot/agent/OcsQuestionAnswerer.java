package com.weida.bot.wechatilinkbot.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.model.OcsAnswerCandidate;
import com.weida.bot.wechatilinkbot.model.OcsQuestionSnapshot;
import com.weida.bot.wechatilinkbot.model.OcsQuestionSnapshot.QuestionType;
import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Uses the existing provider-neutral Agent model as an online OCS answer source. */
@Component
public class OcsQuestionAnswerer {

    private static final String ANSWER_TOOL = "submit_ocs_answer_candidate";
    private static final String ANSWER_SCHEMA = """
            {
              "type":"object",
              "additionalProperties":false,
              "properties":{
                "selectedOptionIds":{
                  "type":"array",
                  "maxItems":20,
                  "items":{"type":"string","maxLength":16}
                },
                "textAnswer":{"type":"string","maxLength":1000},
                "rationale":{"type":"string","maxLength":1000},
                "confidence":{"type":"number","minimum":0,"maximum":1}
              },
              "required":["selectedOptionIds","textAnswer","rationale","confidence"]
            }
            """;
    private static final ToolDefinition ANSWER_DEFINITION = new ToolDefinition(
            ANSWER_TOOL,
            "提交一道课程互动题的结构化答案候选。选择题只能使用题目给出的选项 ID。",
            ANSWER_SCHEMA,
            true);
    private static final String SYSTEM_PROMPT = """
            你是课程互动题的在线答题器。只分析用户提供的这一道题，不执行网页操作。
            必须调用 submit_ocs_answer_candidate，不能只返回自然语言。
            单选和判断题选择一个 option id，多选题选择全部正确 option id，填空题填写 textAnswer。
            不确定时降低 confidence；不得编造不存在的选项 ID。
            rationale 只写简短依据，不复述完整题目。
            """;

    private final AgentModelClient modelClient;
    private final OcsAnswerProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public OcsQuestionAnswerer(
            AgentModelClient modelClient,
            OcsAnswerProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("ocsAnswerExecutor") ExecutorService executor) {
        this.modelClient = modelClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public boolean available() {
        return properties.isEnabled() && modelClient.available();
    }

    public double minimumConfidence() {
        return properties.getMinimumConfidence();
    }

    public OcsAnswerCandidate answer(OcsQuestionSnapshot question) {
        if (!available()) {
            throw new OcsQuestionAnswerException(
                    OcsQuestionAnswerException.Reason.UNAVAILABLE,
                    "OCS 在线 AI 答题当前不可用");
        }
        if (question.type() == QuestionType.UNKNOWN) {
            throw new OcsQuestionAnswerException(
                    OcsQuestionAnswerException.Reason.UNSUPPORTED,
                    "当前题型暂不支持自动回答");
        }

        ModelRequest request = modelRequest(question);
        Future<ModelResponse> future;
        try {
            future = executor.submit(() -> modelClient.complete(request));
        } catch (RejectedExecutionException exception) {
            throw new OcsQuestionAnswerException(
                    OcsQuestionAnswerException.Reason.OVERLOADED,
                    "OCS 在线 AI 答题任务已达到并发上限",
                    exception);
        }

        ModelResponse response;
        try {
            response = future.get(
                    properties.getRequestTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new OcsQuestionAnswerException(
                    OcsQuestionAnswerException.Reason.TIMEOUT,
                    "OCS 在线 AI 答题超时",
                    exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new OcsQuestionAnswerException(
                    OcsQuestionAnswerException.Reason.PROVIDER_FAILURE,
                    "OCS 在线 AI 答题被中断",
                    exception);
        } catch (ExecutionException exception) {
            throw providerFailure(exception.getCause());
        }

        OcsAnswerCandidate candidate = parseCandidate(question, response);
        if (candidate.confidence() < properties.getMinimumConfidence()) {
            throw new OcsQuestionAnswerException(
                    OcsQuestionAnswerException.Reason.LOW_CONFIDENCE,
                    "OCS 在线 AI 答案置信度不足");
        }
        return candidate;
    }

    private ModelRequest modelRequest(OcsQuestionSnapshot question) {
        String questionJson;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("questionId", question.questionId());
            payload.put("type", question.type().name());
            payload.put("stem", question.stem());
            payload.put("options", question.options().stream()
                    .map(option -> Map.of("id", option.id(), "text", option.text()))
                    .toList());
            payload.put("lessonTitle", question.lessonTitle());
            payload.put("context", question.context());
            questionJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new OcsQuestionAnswerException(
                    OcsQuestionAnswerException.Reason.INVALID_RESPONSE,
                    "无法序列化课程互动题",
                    exception);
        }
        return new ModelRequest(
                List.of(
                        ModelMessage.system(SYSTEM_PROMPT),
                        ModelMessage.user(questionJson, List.of())),
                List.of(ANSWER_DEFINITION),
                true);
    }

    private OcsAnswerCandidate parseCandidate(
            OcsQuestionSnapshot question,
            ModelResponse response) {
        if (response.toolCalls().size() != 1
                || !ANSWER_TOOL.equals(response.toolCalls().getFirst().name())) {
            throw invalidResponse("模型没有返回唯一的 OCS 答案工具调用", null);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(response.toolCalls().getFirst().arguments());
        } catch (JsonProcessingException exception) {
            throw invalidResponse("模型返回的 OCS 答案不是合法 JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw invalidResponse("模型返回的 OCS 答案结构不正确", null);
        }

        List<String> selectedOptionIds = optionIds(root.path("selectedOptionIds"));
        String textAnswer = boundedText(root.path("textAnswer"), "textAnswer", 1_000);
        String rationale = boundedText(root.path("rationale"), "rationale", 1_000);
        double confidence = confidence(root.path("confidence"));
        validateAnswerForQuestion(question, selectedOptionIds, textAnswer);
        return new OcsAnswerCandidate(
                question.questionId(),
                selectedOptionIds,
                textAnswer,
                rationale,
                confidence,
                modelIdentifier());
    }

    private List<String> optionIds(JsonNode node) {
        if (!node.isArray() || node.size() > 20) {
            throw invalidResponse("selectedOptionIds 必须是有界数组", null);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw invalidResponse("选项 ID 必须是字符串", null);
            }
            String value = item.textValue().strip().toUpperCase(Locale.ROOT);
            if (value.isBlank() || value.length() > 16) {
                throw invalidResponse("选项 ID 为空或过长", null);
            }
            unique.add(value);
        }
        return List.copyOf(unique);
    }

    private static String boundedText(JsonNode node, String field, int maxCharacters) {
        if (!node.isTextual()) {
            throw invalidResponse(field + " 必须是字符串", null);
        }
        String value = node.textValue().strip();
        if (value.length() > maxCharacters) {
            throw invalidResponse(field + " 过长", null);
        }
        return value;
    }

    private static double confidence(JsonNode node) {
        if (!node.isNumber()) {
            throw invalidResponse("confidence 必须是数字", null);
        }
        double value = node.doubleValue();
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw invalidResponse("confidence 超出范围", null);
        }
        return value;
    }

    private static void validateAnswerForQuestion(
            OcsQuestionSnapshot question,
            List<String> selectedOptionIds,
            String textAnswer) {
        Set<String> validOptionIds = question.optionIdSet();
        if (!validOptionIds.containsAll(selectedOptionIds)) {
            throw invalidResponse("模型选择了题目中不存在的选项", null);
        }
        switch (question.type()) {
            case SINGLE_CHOICE, TRUE_FALSE -> {
                if (selectedOptionIds.size() != 1 || !textAnswer.isBlank()) {
                    throw invalidResponse("单选或判断题必须且只能选择一个选项", null);
                }
            }
            case MULTIPLE_CHOICE -> {
                if (selectedOptionIds.isEmpty() || !textAnswer.isBlank()) {
                    throw invalidResponse("多选题必须选择至少一个选项", null);
                }
            }
            case FILL_BLANK -> {
                if (!selectedOptionIds.isEmpty() || textAnswer.isBlank()) {
                    throw invalidResponse("填空题必须返回文本答案", null);
                }
            }
            case UNKNOWN -> throw invalidResponse("未知题型不能自动回答", null);
        }
    }

    private static OcsQuestionAnswerException providerFailure(Throwable cause) {
        Throwable safeCause = cause instanceof IOException ? cause : null;
        return new OcsQuestionAnswerException(
                OcsQuestionAnswerException.Reason.PROVIDER_FAILURE,
                "OCS 在线 AI 答题请求失败",
                safeCause);
    }

    private String modelIdentifier() {
        String identifier = modelClient.modelIdentifier();
        if (identifier == null || identifier.isBlank()) {
            return "agent-model";
        }
        String safeIdentifier = identifier.strip();
        return safeIdentifier.length() <= 64
                ? safeIdentifier
                : safeIdentifier.substring(0, 64);
    }

    private static OcsQuestionAnswerException invalidResponse(
            String message,
            Throwable cause) {
        return new OcsQuestionAnswerException(
                OcsQuestionAnswerException.Reason.INVALID_RESPONSE,
                message,
                cause);
    }
}
