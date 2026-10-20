package com.weida.bot.wechatilinkbot.agent;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bounded policy for OCS online answers produced by the existing Agent model. */
@Validated
@ConfigurationProperties(prefix = "bot.agent.ocs-answer")
public class OcsAnswerProperties {

    private boolean enabled = true;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double minimumConfidence = 0.7;

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(35);

    @Min(1)
    @Max(8)
    private int maxConcurrentAnswers = 2;

    @Min(0)
    @Max(32)
    private int queueCapacity = 4;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getMinimumConfidence() {
        return minimumConfidence;
    }

    public void setMinimumConfidence(double minimumConfidence) {
        this.minimumConfidence = minimumConfidence;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxConcurrentAnswers() {
        return maxConcurrentAnswers;
    }

    public void setMaxConcurrentAnswers(int maxConcurrentAnswers) {
        this.maxConcurrentAnswers = maxConcurrentAnswers;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    @AssertTrue(message = "OCS 在线答题超时必须在 1 秒到 2 分钟之间")
    public boolean isRequestTimeoutValid() {
        return requestTimeout != null
                && requestTimeout.compareTo(Duration.ofSeconds(1)) >= 0
                && requestTimeout.compareTo(Duration.ofMinutes(2)) <= 0;
    }
}
