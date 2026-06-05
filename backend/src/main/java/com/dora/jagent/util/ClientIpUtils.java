package com.dora.jagent.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class ClientIpUtils {

    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_CLIENT_IP"
    };

    private ClientIpUtils() {
    }

    public static String resolveClientIp(HttpServletRequest request) {
        for (String header : IP_HEADER_CANDIDATES) {
            String value = request.getHeader(header);
            if (!StringUtils.hasText(value) || "unknown".equalsIgnoreCase(value)) {
                continue;
            }
            String firstIp = value.split(",")[0].trim();
            if (StringUtils.hasText(firstIp)) {
                return normalize(firstIp);
            }
        }
        return normalize(request.getRemoteAddr());
    }

    public static boolean isPrivateOrLocalIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return true;
        }
        String normalized = normalize(ip);
        return "127.0.0.1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized)
                || "::1".equals(normalized)
                || normalized.startsWith("10.")
                || normalized.startsWith("192.168.")
                || normalized.startsWith("172.16.")
                || normalized.startsWith("172.17.")
                || normalized.startsWith("172.18.")
                || normalized.startsWith("172.19.")
                || normalized.startsWith("172.20.")
                || normalized.startsWith("172.21.")
                || normalized.startsWith("172.22.")
                || normalized.startsWith("172.23.")
                || normalized.startsWith("172.24.")
                || normalized.startsWith("172.25.")
                || normalized.startsWith("172.26.")
                || normalized.startsWith("172.27.")
                || normalized.startsWith("172.28.")
                || normalized.startsWith("172.29.")
                || normalized.startsWith("172.30.")
                || normalized.startsWith("172.31.");
    }

    private static String normalize(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "";
        }
        String normalized = ip.trim();
        if (normalized.startsWith("::ffff:")) {
            return normalized.substring(7);
        }
        return normalized;
    }
}
