package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/**
 * Strongly typed weather tool input.
 *
 * @param location city, county, district, or region name
 * @author ryx
 * @since 2026-07-23
 */
public record WeatherToolInput(
        @ToolParameter(description = "城市、县区或地区名称") String location) {
}
