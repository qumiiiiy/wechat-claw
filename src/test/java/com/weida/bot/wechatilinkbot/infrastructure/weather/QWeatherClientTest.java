package com.weida.bot.wechatilinkbot.infrastructure.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;

class QWeatherClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsStructuredErrorWhenQWeatherCredentialIsMissing() throws Exception {
        QWeatherProperties weather = new QWeatherProperties();
        weather.setApiKey("");
        QWeatherClient client = client(weather);

        JsonNode result = objectMapper.readTree(client.getCurrentWeather("杭州"));

        assertThat(result.path("provider").asText()).isEqualTo("QWeather");
        assertThat(result.path("status").asText()).isEqualTo("error");
        assertThat(result.path("error_code").asText())
                .isEqualTo("QWEATHER_CREDENTIAL_MISSING");
        assertThat(result.path("message").asText()).contains("API Key");
    }

    @Test
    void returnsStructuredErrorWhenLocationIsMissing() throws Exception {
        QWeatherProperties weather = new QWeatherProperties();
        weather.setApiKey("not-used");
        QWeatherClient client = client(weather);

        JsonNode result = objectMapper.readTree(client.getCurrentWeather("  "));

        assertThat(result.path("status").asText()).isEqualTo("error");
        assertThat(result.path("error_code").asText()).isEqualTo("LOCATION_REQUIRED");
    }

    @Test
    void acceptsTheHostnameFormatCopiedFromQWeatherConsole() {
        URI uri = QWeatherClient.endpoint(
                URI.create("abc123.qweatherapi.com"),
                "/v7/weather/now",
                "location=101210101");

        assertThat(uri).isEqualTo(URI.create(
                "https://abc123.qweatherapi.com/v7/weather/now?location=101210101"));
    }

    private QWeatherClient client(QWeatherProperties weather) {
        return new QWeatherClient(weather, objectMapper);
    }
}
