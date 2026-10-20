package com.weida.bot.wechatilinkbot.infrastructure.weather;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated QWeather provider configuration.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Validated
@ConfigurationProperties(prefix = "bot.infrastructure.weather.qweather")
public class QWeatherProperties {

    private boolean enabled = true;

    @NotNull
    private URI apiHost = URI.create("https://devapi.qweather.com");

    private String apiKey = "";

    private String language = "zh";

    private String unit = "m";

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(10);

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getApiHost() {
        return apiHost;
    }

    public void setApiHost(URI apiHost) {
        this.apiHost = apiHost;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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
