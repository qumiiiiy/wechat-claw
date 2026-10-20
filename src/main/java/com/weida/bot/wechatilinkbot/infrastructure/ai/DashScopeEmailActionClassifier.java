package com.weida.bot.wechatilinkbot.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.agent.AgentModelClient;
import com.weida.bot.wechatilinkbot.agent.ModelMessage;
import com.weida.bot.wechatilinkbot.agent.ModelRequest;
import com.weida.bot.wechatilinkbot.agent.ModelResponse;
import com.weida.bot.wechatilinkbot.capability.EmailActionClassifierPort;
import com.weida.bot.wechatilinkbot.capability.EmailClassificationUnavailableException;
import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Batch email action classification backed by the existing provider-neutral model client.
 *
 * <p>The model performs the semantic judgment for a bounded batch of subjects in one request.
 * Any unavailability, timeout, malformed response or missing decision falls back to the
 * deterministic rule classifier in the capability.</p>
 */
@Component
public class DashScopeEmailActionClassifier implements EmailActionClassifierPort {

    private static final String CLASSIFY_TOOL = "classify_email_actions";
    private static final String CLASSIFY_SCHEMA = """
            {
              "type":"object",
              "additionalProperties":false,
              "properties":{
                "decisions":{
                  "type":"array",
                  "maxItems":100,
                  "items":{
                    "type":"object",
                    "additionalProperties":false,
                    "properties":{
                      "index":{"type":"integer","minimum":0},
                      "actionable":{"type":"boolean"}
                    },
                    "required":["index","actionable"]
                  }
                }
              },
              "required":["decisions"]
            }
            """;
    private static final ToolDefinition CLASSIFY_DEFINITION = new ToolDefinition(
            CLASSIFY_TOOL,
            "批量判断每封邮件主题是否明确需要用户采取行动，返回 decisions。",
            CLASSIFY_SCHEMA,
            true);
    private static final String SYSTEM_PROMPT = """
            你是邮件行动分类器。判断每封邮件主题是否明确需要用户采取行动
            （回复、提交、确认、审批、报名、登记、参加、处理或带有明确截止时间等）。
            退订、验证码、系统通知、自动回复、订单/物流、账单、广告促销、社交平台动态、
            安全更新、网盘提醒等不算需要行动。拿不准时一律标记为不需要行动。
            必须调用 classify_email_actions 返回 decisions，每项的 index 对应输入顺序，
            不要编造输入中不存在的 index。
            """;

    private final AgentModelClient modelClient;
    private final EmailClassificationProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public DashScopeEmailActionClassifier(
            AgentModelClient modelClient,
            EmailClassificationProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("emailClassificationExecutor") ExecutorService executor) {
        this.modelClient = modelClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    @Override
    public boolean available() {
        return properties.isEnabled() && modelClient.available();
    }

    @Override
    public List<EmailActionClassifierPort.Decision> classify(List<String> subjects) {
        if (!available()) {
            throw new EmailClassificationUnavailableException("邮件行动分类模型当前不可用");
        }
        List<String> boundedSubjects = subjects == null ? List.of() : List.copyOf(subjects);
        if (boundedSubjects.isEmpty()) {
            return List.of();
        }
        if (boundedSubjects.size() > properties.getMaxSubjectsPerBatch()) {
            throw new EmailClassificationUnavailableException("邮件数量超过单批分类上限");
        }
        List<String> truncatedSubjects = boundedSubjects.stream()
                .map(subject -> truncate(subject == null ? "" : subject))
                .toList();

        Future<ModelResponse> future;
        try {
            future = executor.submit(() -> modelClient.complete(
                    modelRequest(truncatedSubjects)));
        } catch (RejectedExecutionException exception) {
            throw new EmailClassificationUnavailableException(
                    "邮件行动分类任务已达并发上限",
                    exception);
        }

        ModelResponse response;
        try {
            response = future.get(
                    properties.getRequestTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new EmailClassificationUnavailableException(
                    "邮件行动分类模型请求超时",
                    exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new EmailClassificationUnavailableException(
                    "邮件行动分类模型请求被中断",
                    exception);
        } catch (ExecutionException exception) {
            throw new EmailClassificationUnavailableException(
                    "邮件行动分类模型请求失败",
                    exception.getCause() instanceof IOException ? exception.getCause() : null);
        }

        Set<Integer> decisions = parseDecisions(boundedSubjects.size(), response);
        List<EmailActionClassifierPort.Decision> result =
                new ArrayList<>(boundedSubjects.size());
        for (int index = 0; index < boundedSubjects.size(); index++) {
            result.add(new EmailActionClassifierPort.Decision(
                    boundedSubjects.get(index),
                    decisions.contains(index)));
        }
        return List.copyOf(result);
    }

    private ModelRequest modelRequest(List<String> subjects) {
        String subjectJson;
        try {
            subjectJson = objectMapper.writeValueAsString(subjects);
        } catch (JsonProcessingException exception) {
            throw new EmailClassificationUnavailableException(
                    "无法序列化邮件主题",
                    exception);
        }
        return new ModelRequest(
                List.of(
                        ModelMessage.system(SYSTEM_PROMPT),
                        ModelMessage.user(subjectJson, List.of())),
                List.of(CLASSIFY_DEFINITION),
                true);
    }

    private Set<Integer> parseDecisions(int subjectCount, ModelResponse response) {
        if (response.toolCalls().size() != 1
                || !CLASSIFY_TOOL.equals(response.toolCalls().getFirst().name())) {
            throw invalidResponse("模型没有返回唯一的邮件分类工具调用", null);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(response.toolCalls().getFirst().arguments());
        } catch (JsonProcessingException exception) {
            throw invalidResponse("模型返回的邮件分类结果不是合法 JSON", exception);
        }
        if (root == null || !root.isObject() || !root.path("decisions").isArray()) {
            throw invalidResponse("模型返回的邮件分类结构不正确", null);
        }
        JsonNode decisionsNode = root.path("decisions");
        if (decisionsNode.size() > subjectCount) {
            throw invalidResponse("模型返回了过多分类项", null);
        }
        Set<Integer> actionableIndices = new HashSet<>();
        for (JsonNode item : decisionsNode) {
            if (!item.path("index").isInt() || !item.path("actionable").isBoolean()) {
                throw invalidResponse("邮件分类项格式不正确", null);
            }
            int index = item.path("index").intValue();
            if (index < 0 || index >= subjectCount) {
                throw invalidResponse("模型返回了不存在的邮件序号", null);
            }
            if (item.path("actionable").booleanValue() && !actionableIndices.add(index)) {
                throw invalidResponse("模型重复返回了同一邮件序号", null);
            }
        }
        return actionableIndices;
    }

    private String truncate(String value) {
        int maxCharacters = properties.getMaxSubjectCharacters();
        return value.length() <= maxCharacters
                ? value
                : value.substring(0, maxCharacters);
    }

    private static EmailClassificationUnavailableException invalidResponse(
            String message,
            Throwable cause) {
        return new EmailClassificationUnavailableException(message, cause);
    }
}
