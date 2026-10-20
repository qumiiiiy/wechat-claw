package com.weida.bot.wechatilinkbot.agent.intent;

import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects current-weather requests that include usable location context.
 *
 * @author ryx
 * @since 2026-07-23
 */
@Component
public class WeatherIntentDetector {

    private static final Pattern WEATHER_PATTERN = Pattern.compile(
            "(?:天气|气温|温度|下雨|降雨|晴天|阴天|多云)");
    private static final Pattern LOCATION_CONTEXT_PATTERN = Pattern.compile(
            "(?:我|他|她|对方)?(?:最近|目前|现在)?(?:在|位于|住在|身处)"
                    + "[\\p{IsHan}A-Za-z·\\-]{2,30}"
                    + "|(?:城市|地点|位置)(?:是|为|在)"
                    + "[\\p{IsHan}A-Za-z·\\-]{2,30}");
    private static final Pattern DIRECT_LOCATION_PATTERN = Pattern.compile(
            "(?:^|[，。！？、\\s])(?:就是|查询|查一下|看看)?"
                    + "([\\p{IsHan}A-Za-z·\\-]{2,20}?)(?:市|县|区|州|省)?"
                    + "(?:今天|今日|现在|当前)?(?:的)?天气");
    private static final Pattern TIME_PREFIXED_LOCATION_PATTERN = Pattern.compile(
            "(?:今天|今日|现在|当前)(?:的)?"
                    + "([\\p{IsHan}A-Za-z·\\-]{2,20}?)(?:市|县|区|州|省)?(?:的)?天气");
    private static final Set<String> NON_LOCATION_SET = Set.of(
            "今天", "今日", "现在", "当前", "当地", "这里", "那边");

    /**
     * Returns whether a current-weather request has explicit location context.
     *
     * @param text current user text
     * @return {@code true} when weather and a usable location are both present
     */
    public boolean hasResolvableQuery(String text) {
        String normalizedText = normalize(text);
        if (!hasQuery(normalizedText)) {
            return false;
        }
        return LOCATION_CONTEXT_PATTERN.matcher(normalizedText).find()
                || hasUsableLocation(DIRECT_LOCATION_PATTERN, normalizedText)
                || hasUsableLocation(TIME_PREFIXED_LOCATION_PATTERN, normalizedText);
    }

    /** Returns whether the text requests current or forecast weather information. */
    public boolean hasQuery(String text) {
        return WEATHER_PATTERN.matcher(normalize(text)).find();
    }

    private static boolean hasUsableLocation(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String location = matcher.group(1);
            boolean containsControlText = location.contains("帮我")
                    || location.contains("发送")
                    || location.contains("邮箱")
                    || location.contains("邮件");
            if (!containsControlText && !NON_LOCATION_SET.contains(location)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : Normalizer.normalize(text.strip(), Normalizer.Form.NFKC);
    }
}
