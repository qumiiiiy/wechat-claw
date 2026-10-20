package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.CURRENT_WEATHER;

import com.weida.bot.wechatilinkbot.capability.WeatherCapability;
import com.weida.bot.wechatilinkbot.tool.input.WeatherToolInput;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Agent tool that exposes QWeather current conditions and a seven-day forecast.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Order(10)
@Component
public class CurrentWeatherTool implements AgentTool<WeatherToolInput> {

    private final WeatherCapability weatherCapability;

    public CurrentWeatherTool(WeatherCapability weatherCapability) {
        this.weatherCapability = weatherCapability;
    }

    @Override
    public String name() {
        return CURRENT_WEATHER;
    }

    @Override
    public String description() {
        return "通过和风天气 QWeather 查询指定城市、县区或地区的实时天气及未来7天预报。"
                + "用户询问天气、温度、湿度、风速、降水、紫外线或未来几天天气时使用；"
                + "地点不明确时应先向用户确认。";
    }

    @Override
    public Class<WeatherToolInput> inputType() {
        return WeatherToolInput.class;
    }

    @Override
    public boolean available() {
        return weatherCapability.available();
    }

    @Override
    public boolean producesMedia() {
        return false;
    }

    @Override
    public ToolExecution execute(
            WeatherToolInput input,
            ToolContext context) {
        String result = weatherCapability.getCurrentWeather(input.location());
        return new ToolExecution(result, null);
    }
}
