package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WeatherIntentDetectorTest {

    private final WeatherIntentDetector detector = new WeatherIntentDetector();

    @Test
    void requiresLocationContextBeforeRoutingToTheWeatherTool() {
        assertThat(detector.hasResolvableQuery("帮我把今天的天气发送到邮箱")).isFalse();
        assertThat(detector.hasResolvableQuery(
                "就是杭州的天气，我最近在杭州")).isTrue();
        assertThat(detector.hasResolvableQuery("北京今天的天气怎么样")).isTrue();
        assertThat(detector.hasResolvableQuery(
                "帮我发送今天杭州的天气到 friend@example.com")).isTrue();
    }
}
