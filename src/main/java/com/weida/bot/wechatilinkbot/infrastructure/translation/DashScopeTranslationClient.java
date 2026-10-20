package com.weida.bot.wechatilinkbot.infrastructure.translation;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.protocol.ConnectionOptions;
import com.weida.bot.wechatilinkbot.infrastructure.ai.DashScopeModelProperties;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Narrow DashScope adapter for text translation via chat completion.
 *
 * @author ryx
 * @since 2026-07-24
 */
@Component
public class DashScopeTranslationClient {

    private static final Logger LOG = LoggerFactory.getLogger(DashScopeTranslationClient.class);
    private static final String HTTP_PROTOCOL = "http";
    private static final String SYSTEM_PROMPT =
            "你是一个专业翻译。只输出译文，不要解释、不要原文、不要任何额外文字。";

    private final DashScopeTranslationProperties translationProperties;
    private final DashScopeModelProperties modelProperties;
    private final ProviderClient providerClient;

    @Autowired
    public DashScopeTranslationClient(
            DashScopeTranslationProperties translationProperties,
            DashScopeModelProperties modelProperties) {
        this(translationProperties, modelProperties, createProviderClient(translationProperties, modelProperties));
    }

    DashScopeTranslationClient(
            DashScopeTranslationProperties translationProperties,
            DashScopeModelProperties modelProperties,
            ProviderClient providerClient) {
        this.translationProperties = translationProperties;
        this.modelProperties = modelProperties;
        this.providerClient = providerClient;
    }

    public boolean isAvailable() {
        return translationProperties.isEnabled()
                && modelProperties.getApiKey() != null
                && !modelProperties.getApiKey().isBlank();
    }

    public String translate(String text, String targetLanguage, String sourceLanguage) {
        if (!isAvailable()) {
            return "{\"error\":\"翻译服务当前不可用\"}";
        }
        if (text == null || text.isBlank()) {
            return "{\"error\":\"待翻译文本不能为空\"}";
        }
        if (text.length() > translationProperties.getMaxTextLength()) {
            return "{\"error\":\"文本超过最大长度限制 " + translationProperties.getMaxTextLength() + " 字符\"}";
        }
        String userPrompt = buildUserPrompt(text, targetLanguage, sourceLanguage);
        MultiModalConversationParam request = buildRequest(userPrompt);
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= translationProperties.getMaxAttempts(); attempt++) {
            try {
                return extractTranslation(providerClient.call(request));
            } catch (Exception exception) {
                lastFailure = exception;
                LOG.warn("translation_attempt_failed model={} attempt={} causeType={}",
                        translationProperties.getModel(), attempt, exception.getClass().getSimpleName());
            }
        }
        LOG.error("translation_request_failed model={} attempts={}",
                translationProperties.getModel(), translationProperties.getMaxAttempts(), lastFailure);
        return "{\"error\":\"翻译请求失败，请稍后重试\"}";
    }

    private String buildUserPrompt(String text, String targetLanguage, String sourceLanguage) {
        if (sourceLanguage != null && !sourceLanguage.isBlank()) {
            return "将以下" + sourceLanguage + "文本翻译为" + targetLanguage + "：\n\n" + text;
        }
        return "将以下文本翻译为" + targetLanguage + "：\n\n" + text;
    }

    private MultiModalConversationParam buildRequest(String userPrompt) {
        return MultiModalConversationParam.builder()
                .apiKey(modelProperties.getApiKey().strip())
                .model(translationProperties.getModel())
                .messages(List.of(
                        MultiModalMessage.builder()
                                .role("system")
                                .content(List.of(Map.of("text", SYSTEM_PROMPT)))
                                .build(),
                        MultiModalMessage.builder()
                                .role("user")
                                .content(List.of(Map.of("text", userPrompt)))
                                .build()))
                .temperature(0.3f)
                .maxTokens(4096)
                .enableThinking(false)
                .build();
    }

    private String extractTranslation(MultiModalConversationResult result) throws IOException {
        if (result == null || result.getOutput() == null) {
            throw new IOException("翻译模型返回了空响应");
        }
        MultiModalConversationOutput output = result.getOutput();
        if (output.getChoices() == null || output.getChoices().isEmpty()) {
            throw new IOException("翻译模型响应中缺少 choices");
        }
        MultiModalMessage message = output.getChoices().getFirst().getMessage();
        if (message == null || message.getContent() == null) {
            throw new IOException("翻译模型返回了空结果");
        }
        String translation = message.getContent().stream()
                .map(item -> item.get("text"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.joining());
        if (translation.isBlank()) {
            throw new IOException("翻译模型返回了空内容");
        }
        return "{\"translation\":\"" + escapeJson(translation.strip()) + "\"}";
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static ProviderClient createProviderClient(
            DashScopeTranslationProperties translationProperties,
            DashScopeModelProperties modelProperties) {
        ConnectionOptions options = ConnectionOptions.builder()
                .connectTimeout(translationProperties.getConnectTimeout())
                .writeTimeout(translationProperties.getRequestTimeout())
                .readTimeout(translationProperties.getRequestTimeout())
                .build();
        return request -> new MultiModalConversation(
                        HTTP_PROTOCOL,
                        modelProperties.getBaseUrl().toString(),
                        options)
                .call(request);
    }

    @FunctionalInterface
    interface ProviderClient {

        MultiModalConversationResult call(MultiModalConversationParam request) throws Exception;
    }
}
