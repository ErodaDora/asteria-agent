package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.service.WeatherQueryService;
import com.dora.jagent.service.impl.support.WeatherSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeatherQueryServiceImpl implements WeatherQueryService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, List<String>> LOCATION_ALIASES = buildLocationAliases();
    private static final Map<String, PresetLocation> PRESET_LOCATIONS = buildPresetLocations();

    @Override
    public WeatherSnapshot getTodayWeather(String location) {
        if (!StringUtils.hasText(location)) {
            throw new BizException("location cannot be blank");
        }

        String normalizedLocation = location.trim();
        try {
            JsonNode geoLocation = queryLocation(normalizedLocation);
            PresetLocation presetLocation = PRESET_LOCATIONS.get(normalizedLocation);
            if (geoLocation == null && presetLocation == null) {
                throw new BizException("未找到匹配城市，请尝试输入更具体的城市名");
            }

            String resolvedName = geoLocation != null ? buildResolvedName(geoLocation) : presetLocation.resolvedName();
            double latitude = geoLocation != null ? geoLocation.path("latitude").asDouble() : presetLocation.latitude();
            double longitude = geoLocation != null ? geoLocation.path("longitude").asDouble() : presetLocation.longitude();

            return buildWeatherSnapshot(normalizedLocation, resolvedName, latitude, longitude);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("查询天气失败：" + e.getMessage());
        }
    }

    @Override
    public WeatherSnapshot getTodayWeather(String resolvedLocation, double latitude, double longitude) {
        if (!StringUtils.hasText(resolvedLocation)) {
            throw new BizException("resolved location cannot be blank");
        }
        try {
            return buildWeatherSnapshot(resolvedLocation, resolvedLocation, latitude, longitude);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("查询天气失败：" + e.getMessage());
        }
    }

    private JsonNode queryLocation(String location) throws Exception {
        for (String candidate : expandLocationCandidates(location)) {
            JsonNode result = queryLocationCandidate(candidate);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private JsonNode queryLocationCandidate(String candidate) throws Exception {
        String encoded = URLEncoder.encode(candidate, StandardCharsets.UTF_8);
        URI uri = URI.create("https://geocoding-api.open-meteo.com/v1/search?name=" + encoded
                + "&count=1&language=zh&format=json");
        JsonNode root = sendJsonRequest(uri);
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    private JsonNode queryForecast(double latitude, double longitude) throws Exception {
        String forecastUrl = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&timezone=auto"
                + "&forecast_days=1";
        return sendJsonRequest(URI.create(forecastUrl));
    }

    private JsonNode sendJsonRequest(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("天气服务响应异常，status=" + response.statusCode());
        }
        return OBJECT_MAPPER.readTree(response.body());
    }

    private WeatherSnapshot buildWeatherSnapshot(
            String requestedLocation,
            String resolvedLocation,
            double latitude,
            double longitude
    ) throws Exception {
        JsonNode forecast = queryForecast(latitude, longitude);
        JsonNode current = forecast.path("current");
        JsonNode daily = forecast.path("daily");
        int weatherCode = current.path("weather_code").asInt();

        return WeatherSnapshot.builder()
                .requestedLocation(requestedLocation)
                .resolvedLocation(resolvedLocation)
                .weatherCode(weatherCode)
                .weatherText(weatherCodeToChinese(weatherCode))
                .currentTemperature(current.path("temperature_2m").asDouble())
                .apparentTemperature(current.path("apparent_temperature").asDouble())
                .minTemperature(daily.path("temperature_2m_min").path(0).asDouble())
                .maxTemperature(daily.path("temperature_2m_max").path(0).asDouble())
                .currentPrecipitation(current.path("precipitation").asDouble())
                .precipitationProbability(daily.path("precipitation_probability_max").path(0).asInt())
                .windSpeed(current.path("wind_speed_10m").asDouble())
                .build();
    }

    private String buildResolvedName(JsonNode location) {
        String name = location.path("name").asText("");
        String admin1 = location.path("admin1").asText("");
        String country = location.path("country").asText("");
        StringBuilder builder = new StringBuilder(name);
        if (StringUtils.hasText(admin1) && !admin1.equals(name)) {
            builder.append(" / ").append(admin1);
        }
        if (StringUtils.hasText(country)) {
            builder.append(" / ").append(country);
        }
        return builder.toString();
    }

    private String weatherCodeToChinese(int code) {
        return switch (code) {
            case 0 -> "晴朗";
            case 1, 2 -> "晴间多云";
            case 3 -> "阴天";
            case 45, 48 -> "有雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61, 63, 65 -> "降雨";
            case 66, 67 -> "冻雨";
            case 71, 73, 75 -> "降雪";
            case 77 -> "雪粒";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴伴冰雹";
            default -> "天气状况未知";
        };
    }

    private List<String> expandLocationCandidates(String location) {
        List<String> aliases = LOCATION_ALIASES.get(location);
        if (aliases != null && !aliases.isEmpty()) {
            return aliases;
        }
        return List.of(
                location,
                location.replace("市", " ").replace("区", " ").trim(),
                location.replace("市", "").replace("区", "").trim()
        );
    }

    private static Map<String, List<String>> buildLocationAliases() {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("杭州市钱塘区", List.of(
                "杭州市钱塘区",
                "杭州 钱塘区",
                "钱塘区 杭州",
                "Qiantang District Hangzhou",
                "Hangzhou Qiantang District"
        ));
        return aliases;
    }

    private static Map<String, PresetLocation> buildPresetLocations() {
        Map<String, PresetLocation> presets = new LinkedHashMap<>();
        presets.put("杭州市钱塘区", new PresetLocation(
                "杭州市钱塘区 / 浙江 / 中国",
                30.3089,
                120.3372
        ));
        return presets;
    }

    private record PresetLocation(String resolvedName, double latitude, double longitude) {
    }
}
