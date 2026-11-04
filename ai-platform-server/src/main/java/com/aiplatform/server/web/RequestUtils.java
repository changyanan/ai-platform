package com.aiplatform.server.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 请求元信息工具。仅用于审计展示，IP 不作为安全边界。
 */
public final class RequestUtils {

    private RequestUtils() {
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip;
        if (StringUtils.hasText(forwarded) && !"unknown".equalsIgnoreCase(forwarded)) {
            ip = forwarded.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        return ip.length() > 64 ? ip.substring(0, 64) : ip;
    }

    public static String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        return userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
    }
}
