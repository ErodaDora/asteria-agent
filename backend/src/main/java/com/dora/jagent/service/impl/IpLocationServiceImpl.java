package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.service.IpLocationService;
import com.dora.jagent.service.impl.support.IpLocationSnapshot;
import com.dora.jagent.util.ClientIpUtils;
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

@Service
public class IpLocationServiceImpl implements IpLocationService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final IpLocationSnapshot LOCAL_FALLBACK_LOCATION = IpLocationSnapshot.builder()
            .requestedIp("local")
            .resolvedIp("local")
            .country("中国")
            .region("浙江")
            .city("杭州")
            .district("钱塘区")
            .displayName("杭州市钱塘区 / 浙江 / 中国")
            .latitude(30.3089)
            .longitude(120.3372)
            .build();

    @Override
    public IpLocationSnapshot locateByIp(String ip) {
        if (!StringUtils.hasText(ip) || ClientIpUtils.isPrivateOrLocalIp(ip)) {
            return LOCAL_FALLBACK_LOCATION;
        }

        String normalizedIp = ip.trim();
        try {
            JsonNode geoRoot = queryIpLocation(normalizedIp);
            boolean success = geoRoot.path("success").asBoolean(false);
            if (!success) {
                throw new BizException("IP 定位失败，请稍后重试");
            }

            double latitude = geoRoot.path("latitude").asDouble();
            double longitude = geoRoot.path("longitude").asDouble();
            String region = geoRoot.path("region").asText("");
            String city = geoRoot.path("city").asText("");
            String country = geoRoot.path("country").asText("");
            String district = queryDistrict(latitude, longitude);

            return IpLocationSnapshot.builder()
                    .requestedIp(normalizedIp)
                    .resolvedIp(geoRoot.path("ip").asText(normalizedIp))
                    .country(country)
                    .region(region)
                    .city(city)
                    .district(district)
                    .displayName(buildDisplayName(district, city, region, country))
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("IP 定位失败：" + e.getMessage());
        }
    }

    private JsonNode queryIpLocation(String ip) throws Exception {
        String encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8);
        URI uri = URI.create("https://ipwho.is/" + encodedIp + "?lang=zh");
        return sendJsonRequest(uri, false);
    }

    private String queryDistrict(double latitude, double longitude) throws Exception {
        String reverseUrl = "https://nominatim.openstreetmap.org/reverse"
                + "?lat=" + latitude
                + "&lon=" + longitude
                + "&format=jsonv2"
                + "&accept-language=zh-CN";
        JsonNode root = sendJsonRequest(URI.create(reverseUrl), true);
        JsonNode address = root.path("address");
        if (address.isMissingNode()) {
            return "";
        }

        String[] candidates = {
                address.path("city_district").asText(""),
                address.path("suburb").asText(""),
                address.path("borough").asText(""),
                address.path("county").asText(""),
                address.path("town").asText("")
        };
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private JsonNode sendJsonRequest(URI uri, boolean nominatim) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .header("Accept", "application/json");
        if (nominatim) {
            requestBuilder.header("User-Agent", "JAgent/1.0 (workspace weather ip resolver)");
        }

        HttpResponse<String> response = HTTP_CLIENT.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("定位服务响应异常，status=" + response.statusCode());
        }
        return OBJECT_MAPPER.readTree(response.body());
    }

    private String buildDisplayName(String district, String city, String region, String country) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(city)) {
            builder.append(city);
        }
        if (StringUtils.hasText(district) && !district.equals(city)) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append(district);
        }
        if (StringUtils.hasText(region)) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(region);
        }
        if (StringUtils.hasText(country)) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(country);
        }
        return builder.length() == 0 ? "当前位置" : builder.toString();
    }
}
