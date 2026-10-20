package com.weida.bot.wechatilinkbot.infrastructure.ai;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bounded policy for model-backed email action classification. */
@Validated
@ConfigurationProperties(prefix = "bot.infrastructure.ai.email-classification")
public class EmailClassificationProperties {

    private boolean enabled = true;

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(15);

    @Min(1)
    @Max(8)
    private int maxConcurrentRequests = 2;

    @Min(0)
    @Max(32)
    private int queueCapacity = 4;

    @Min(1)
    @Max(100)
    private int maxSubjectsPerBatch = 50;

    @Min(1)
    @Max(500)
    private int maxSubjectCharacters = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getMaxSubjectsPerBatch() {
        return maxSubjectsPerBatch;
    }

    public void setMaxSubjectsPerBatch(int maxSubjectsPerBatch) {
        this.maxSubjectsPerBatch = maxSubjectsPerBatch;
    }

    public int getMaxSubjectCharacters() {
        return maxSubjectCharacters;
    }

    public void setMaxSubjectCharacters(int maxSubjectCharacters) {
        this.maxSubjectCharacters = maxSubjectCharacters;
    }

    @AssertTrue(message = "邮件分类模型超时必须在 1 秒到 1 分钟之间")
    public boolean isRequestTimeoutValid() {
        return requestTimeout != null
                && requestTimeout.compareTo(Duration.ofSeconds(1)) >= 0
                && requestTimeout.compareTo(Duration.ofMinutes(1)) <= 0;
    }
}
