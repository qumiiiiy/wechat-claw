package com.weida.bot.wechatilinkbot.infrastructure.ai;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Alibaba Cloud Model Studio chat configuration.
 */
@Validated
@ConfigurationProperties(prefix = "bot.infrastructure.ai.dashscope")
public class DashScopeModelProperties {

    private boolean enabled = true;
    private String apiKey = "";

    @NotNull
    private URI baseUrl = URI.create("https://dashscope.aliyuncs.com/api/v1");

    @NotBlank
    private String model = "qwen3.7-flash";

    @DecimalMin("0.0")
    @DecimalMax("1.99")
    private double temperature = 0.7;

    @Min(64)
    @Max(8192)
    private int maxCompletionTokens = 1200;

    @Min(1)
    @Max(5)
    private int maxAttempts = 3;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(15);

    @NotNull
    private Duration requestTimeout = Duration.ofMinutes(2);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(int maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
