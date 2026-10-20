package com.weida.bot.wechatilinkbot.infrastructure.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import org.springframework.stereotype.Component;

/**
 * Encapsulates QWeather GeoAPI, current weather, and seven-day forecast calls.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class QWeatherClient {

    private static final String PROVIDER_NAME = "QWeather";
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int ERROR_BODY_MAX_CHARACTERS = 300;

    private final QWeatherProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QWeatherClient(
            QWeatherProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Reports whether QWeather can accept authenticated requests.
     *
     * @return provider availability
     */
    public boolean isAvailable() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank();
    }

    /**
     * Gets normalized weather data or a stable structured error document.
     *
     * @param location user-provided place text
     * @return normalized JSON document
     */
    public String getCurrentWeather(String location) {
        String validationError = validateRequest(location);
        if (validationError != null) {
            return validationError;
        }
        String safeLocation = location.strip();
        try {
            JsonNode place = lookupLocation(safeLocation);
            if (place == null) {
                return error("LOCATION_NOT_FOUND", "和风天气未找到地点：" + safeLocation);
            }
            String locationId = place.path("id").asText();
            if (locationId.isBlank()) {
                return error("LOCATION_ID_MISSING", "和风天气地点结果缺少 Location ID");
            }
            return queryAndNormalize(place, locationId).toString();
        } catch (QWeatherApiException exception) {
            return error(exception.getCode(), exception.getMessage());
        } catch (IOException exception) {
            return error(
                    "QWEATHER_IO_ERROR",
                    "和风天气服务暂时不可用：" + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return error("QWEATHER_INTERRUPTED", "和风天气查询被中断");
        } catch (RuntimeException exception) {
            return error(
                    "QWEATHER_RESPONSE_INVALID",
                    "和风天气数据解析失败：" + exception.getMessage());
        }
    }

    private String validateRequest(String location) {
        if (!properties.isEnabled()) {
            return error("WEATHER_DISABLED", "天气查询功能未启用");
        }
        if (location == null || location.isBlank()) {
            return error("LOCATION_REQUIRED", "缺少城市或地区名称");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return error("QWEATHER_CREDENTIAL_MISSING", "未配置和风天气 API Key");
        }
        return null;
    }

    private ObjectNode queryAndNormalize(JsonNode place, String locationId)
            throws IOException, InterruptedException {
        String commonQuery = "location=" + encode(locationId)
                + "&lang=" + encode(language())
                + "&unit=" + encode(unit());
        JsonNode now = getQWeatherJson("/v7/weather/now", commonQuery);
        JsonNode daily = getQWeatherJson("/v7/weather/7d", commonQuery);
        return normalize(place, now, daily);
    }

    private JsonNode lookupLocation(String location)
            throws IOException, InterruptedException {
        String query = "location=" + encode(location)
                + "&number=1&lang=" + encode(language());
        JsonNode root = getQWeatherJson("/geo/v2/city/lookup", query);
        JsonNode locationList = root.path("location");
        return locationList.isArray() && !locationList.isEmpty()
                ? locationList.get(0)
                : null;
    }

    private ObjectNode normalize(JsonNode place, JsonNode nowRoot, JsonNode dailyRoot) {
        WeatherFieldNames fieldNames = weatherFieldNames();
        ObjectNode result = objectMapper.createObjectNode();
        result.put("provider", PROVIDER_NAME);
        result.put("status", "success");
        addUnits(result, fieldNames.imperial());
        addLocation(result, place);
        copyText(nowRoot, result, "updateTime", "update_time");
        copyText(nowRoot, result, "fxLink", "fx_link");
        addCurrentWeather(result, nowRoot.path("now"), fieldNames);
        addDailyForecast(result, dailyRoot.path("daily"), fieldNames);
        addAttribution(result, nowRoot.path("refer"));
        return result;
    }

    private WeatherFieldNames weatherFieldNames() {
        boolean imperial = "i".equals(unit());
        return new WeatherFieldNames(
                imperial,
                imperial ? "temperature_fahrenheit" : "temperature_celsius",
                imperial
                        ? "apparent_temperature_fahrenheit"
                        : "apparent_temperature_celsius",
                imperial ? "speed_mph" : "speed_kmh",
                imperial ? "visibility_miles" : "visibility_km");
    }

    private void addUnits(ObjectNode result, boolean imperial) {
        ObjectNode units = result.putObject("units");
        units.put("temperature", imperial ? "fahrenheit" : "celsius");
        units.put("wind_speed", imperial ? "mile_per_hour" : "kilometer_per_hour");
        units.put("visibility", imperial ? "mile" : "kilometer");
        units.put("precipitation", "millimeter");
        units.put("pressure", "hectopascal");
    }

    private static void addLocation(ObjectNode result, JsonNode place) {
        ObjectNode location = result.putObject("location");
        copyText(place, location, "id", "id");
        copyText(place, location, "name", "name");
        copyText(place, location, "adm1", "administrative_area_level_1");
        copyText(place, location, "adm2", "administrative_area_level_2");
        copyText(place, location, "country", "country");
        copyNumberText(place, location, "lat", "latitude");
        copyNumberText(place, location, "lon", "longitude");
        copyText(place, location, "tz", "timezone");
    }

    private static void addCurrentWeather(
            ObjectNode result,
            JsonNode now,
            WeatherFieldNames fieldNames) {
        ObjectNode current = result.putObject("current");
        copyText(now, current, "obsTime", "observation_time");
        copyText(now, current, "text", "condition");
        copyText(now, current, "icon", "condition_icon");
        copyNumberText(now, current, "temp", fieldNames.temperature());
        copyNumberText(now, current, "feelsLike", fieldNames.apparentTemperature());
        copyNumberText(now, current, "humidity", "relative_humidity_percent");
        copyNumberText(now, current, "precip", "precipitation_mm");
        copyNumberText(now, current, "pressure", "pressure_hpa");
        copyNumberText(now, current, "vis", fieldNames.visibility());
        copyNumberText(now, current, "cloud", "cloud_cover_percent");
        copyNumberText(now, current, "dew", "dew_point_celsius");
        ObjectNode wind = current.putObject("wind");
        copyText(now, wind, "windDir", "direction");
        copyText(now, wind, "windScale", "scale");
        copyNumberText(now, wind, "windSpeed", fieldNames.windSpeed());
        copyNumberText(now, wind, "wind360", "direction_degrees");
    }

    private static void addDailyForecast(
            ObjectNode result,
            JsonNode daily,
            WeatherFieldNames fieldNames) {
        ArrayNode forecast = result.putArray("daily_forecast");
        if (!daily.isArray()) {
            return;
        }
        for (JsonNode sourceDay : daily) {
            ObjectNode day = forecast.addObject();
            addDailySummary(day, sourceDay, fieldNames);
            addDailyWind(day, sourceDay, fieldNames.windSpeed());
        }
    }

    private static void addDailySummary(
            ObjectNode day,
            JsonNode sourceDay,
            WeatherFieldNames fieldNames) {
        copyText(sourceDay, day, "fxDate", "date");
        copyText(sourceDay, day, "sunrise", "sunrise");
        copyText(sourceDay, day, "sunset", "sunset");
        copyText(sourceDay, day, "textDay", "condition_day");
        copyText(sourceDay, day, "textNight", "condition_night");
        copyText(sourceDay, day, "iconDay", "condition_day_icon");
        copyText(sourceDay, day, "iconNight", "condition_night_icon");
        copyNumberText(
                sourceDay, day, "tempMax", "max_" + fieldNames.temperature());
        copyNumberText(
                sourceDay, day, "tempMin", "min_" + fieldNames.temperature());
        copyNumberText(sourceDay, day, "humidity", "relative_humidity_percent");
        copyNumberText(sourceDay, day, "precip", "precipitation_mm");
        copyNumberText(sourceDay, day, "uvIndex", "uv_index");
        copyNumberText(sourceDay, day, "vis", fieldNames.visibility());
    }

    private static void addDailyWind(
            ObjectNode day,
            JsonNode sourceDay,
            String windSpeedField) {
        ObjectNode dayWind = day.putObject("wind_day");
        copyText(sourceDay, dayWind, "windDirDay", "direction");
        copyText(sourceDay, dayWind, "windScaleDay", "scale");
        copyNumberText(sourceDay, dayWind, "windSpeedDay", windSpeedField);
        ObjectNode nightWind = day.putObject("wind_night");
        copyText(sourceDay, nightWind, "windDirNight", "direction");
        copyText(sourceDay, nightWind, "windScaleNight", "scale");
        copyNumberText(sourceDay, nightWind, "windSpeedNight", windSpeedField);
    }

    private void addAttribution(ObjectNode result, JsonNode refer) {
        ObjectNode attribution = result.putObject("attribution");
        attribution.set("sources", copyArray(refer.path("sources")));
        attribution.set("license", copyArray(refer.path("license")));
    }

    private JsonNode getQWeatherJson(String path, String query)
            throws IOException, InterruptedException {
        URI uri = endpoint(properties.getApiHost(), path, query);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout())
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .header("X-QW-Api-Key", properties.getApiKey().strip())
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofByteArray());
        String body = responseBody(response);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new QWeatherApiException(
                    "HTTP_" + response.statusCode(),
                    "和风天气 HTTP " + response.statusCode() + "："
                            + abbreviate(body, ERROR_BODY_MAX_CHARACTERS));
        }
        return successfulBody(body);
    }

    private JsonNode successfulBody(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        String code = root.path("code").asText();
        if (!"200".equals(code)) {
            throw new QWeatherApiException(
                    "QWEATHER_" + (code.isBlank() ? "UNKNOWN" : code),
                    qweatherStatusMessage(code));
        }
        return root;
    }

    private static String responseBody(HttpResponse<byte[]> response) throws IOException {
        byte[] bytes = response.body() == null ? new byte[0] : response.body();
        String encoding = response.headers().firstValue("Content-Encoding").orElse("");
        if ("gzip".equalsIgnoreCase(encoding)) {
            try (GZIPInputStream gzip = new GZIPInputStream(
                    new ByteArrayInputStream(bytes))) {
                bytes = gzip.readAllBytes();
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String error(String code, String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("provider", PROVIDER_NAME);
        node.put("status", "error");
        node.put("error_code", code);
        node.put("message", message);
        return node.toString();
    }

    private ArrayNode copyArray(JsonNode source) {
        ArrayNode target = objectMapper.createArrayNode();
        if (source.isArray()) {
            for (JsonNode value : source) {
                target.add(value.asText());
            }
        }
        return target;
    }

    private static void copyText(
            JsonNode source,
            ObjectNode target,
            String sourceName,
            String targetName) {
        JsonNode value = source.get(sourceName);
        if (value != null && !value.isNull() && !value.asText().isBlank()) {
            target.put(targetName, value.asText());
        }
    }

    private static void copyNumberText(
            JsonNode source,
            ObjectNode target,
            String sourceName,
            String targetName) {
        JsonNode value = source.get(sourceName);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return;
        }
        try {
            target.put(targetName, Double.parseDouble(value.asText()));
        } catch (NumberFormatException exception) {
            target.put(targetName, value.asText());
        }
    }

    private String language() {
        return properties.getLanguage() == null || properties.getLanguage().isBlank()
                ? "zh"
                : properties.getLanguage().strip();
    }

    private String unit() {
        String configuredUnit = properties.getUnit() == null
                ? "m"
                : properties.getUnit().strip().toLowerCase(Locale.ROOT);
        return "i".equals(configuredUnit) ? "i" : "m";
    }

    private Duration timeout() {
        return properties.getRequestTimeout() == null
                ? DEFAULT_REQUEST_TIMEOUT
                : properties.getRequestTimeout();
    }

    static URI endpoint(URI apiHost, String path, String query) {
        String baseUrl = removeTrailingSlashes(apiHost.toString());
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
            baseUrl = "https://" + baseUrl;
        }
        return URI.create(baseUrl + path + "?" + query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String qweatherStatusMessage(String code) {
        return switch (code) {
            case "204" -> "和风天气没有找到匹配的数据";
            case "400" -> "和风天气请求参数错误";
            case "401" -> "和风天气认证失败，请检查 API Key 和 API Host";
            case "402" -> "和风天气请求额度不足或账户欠费";
            case "403" -> "和风天气拒绝访问，请检查服务权限";
            case "404" -> "和风天气请求的资源不存在";
            case "429" -> "和风天气请求过于频繁，请稍后重试";
            case "500" -> "和风天气内部服务异常";
            default -> "和风天气返回业务状态码："
                    + (code == null || code.isBlank() ? "未知" : code);
        };
    }

    private static String removeTrailingSlashes(String value) {
        int endIndex = value.length();
        while (endIndex > 0 && value.charAt(endIndex - 1) == '/') {
            endIndex--;
        }
        return value.substring(0, endIndex);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "…";
    }

    private record WeatherFieldNames(
            boolean imperial,
            String temperature,
            String apparentTemperature,
            String windSpeed,
            String visibility) {
    }

    private static final class QWeatherApiException extends IOException {

        private final String code;

        private QWeatherApiException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String getCode() {
            return code;
        }
    }
}
