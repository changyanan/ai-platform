package com.aiplatform.audit.web;

import com.aiplatform.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端写操作审计：只记录非 GET 的 /api/admin/**，不记录请求体，避免敏感信息落库。
 * 审计写入失败不影响主请求。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class OperationAuditFilter extends OncePerRequestFilter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            String path = request.getRequestURI();
            if (path.startsWith("/api/admin/") && !"GET".equalsIgnoreCase(request.getMethod())) {
                audit(request, response, path, System.currentTimeMillis() - start);
            }
        }
    }

    private void audit(HttpServletRequest request, HttpServletResponse response, String path, long duration) {
        try {
            Long userId = null;
            String username = null;
            try {
                userId = SecurityUtils.currentUserId();
                username = SecurityUtils.currentUsername();
            } catch (Exception ignored) {
                // 未认证或上下文已清理
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("method", request.getMethod());
            detail.put("path", path);
            detail.put("status", response.getStatus());
            detail.put("query", request.getQueryString());
            jdbcTemplate.update("""
                            INSERT INTO ai_operation_audit
                            (user_id, username, module, action, resource_type, resource_id, success,
                             ip, detail_json, duration_ms, trace_id, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                            """,
                    userId,
                    username,
                    moduleOf(path),
                    actionOf(request.getMethod(), path),
                    resourceTypeOf(path),
                    resourceIdOf(path),
                    response.getStatus() < 400,
                    clientIp(request),
                    objectMapper.writeValueAsString(detail),
                    duration,
                    MDC.get("traceId"));
        } catch (Exception ex) {
            log.debug("Operation audit skipped: {}", ex.getMessage());
        }
    }

    private String moduleOf(String path) {
        String[] parts = path.split("/");
        return parts.length > 3 ? limit(parts[3], 64) : "admin";
    }

    private String actionOf(String method, String path) {
        return limit(method + " " + path, 64);
    }

    private String resourceTypeOf(String path) {
        String[] parts = path.split("/");
        if (parts.length <= 4) {
            return null;
        }
        for (int i = 4; i < parts.length; i++) {
            if (!parts[i].matches("\\d+")) {
                return limit(parts[i], 64);
            }
        }
        return null;
    }

    private String resourceIdOf(String path) {
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+")) {
                return parts[i];
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return limit(forwarded.split(",")[0].trim(), 64);
        }
        return limit(request.getRemoteAddr(), 64);
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
