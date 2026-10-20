package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.weather.QWeatherClient;
import org.springframework.stereotype.Component;

/**
 * Default weather service backed by QWeather.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class WeatherCapability {

    private final QWeatherClient qWeatherClient;

    public WeatherCapability(QWeatherClient qWeatherClient) {
        this.qWeatherClient = qWeatherClient;
    }

    public boolean available() {
        return qWeatherClient.isAvailable();
    }

    public String getCurrentWeather(String location) {
        return qWeatherClient.getCurrentWeather(location);
    }
}
