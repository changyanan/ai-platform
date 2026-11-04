package com.aiplatform.server.security;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.server.config.OpenApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 开放接口 API Key 认证。只作用于 /api/open/**，不复用管理员 JWT。
 * 使用常量时间比较，避免时序侧信道。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OpenApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-Key";

    private final OpenApiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenApiKeyFilter(OpenApiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/open/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!properties.isEnabled()) {
            writeError(response, 503, ErrorCode.OPEN_API_DISABLED.getCode(),
                    "开放接口未启用，请先配置 AI_PLATFORM_OPEN_API_KEY");
            return;
        }
        String expected = properties.getKey();
        if (!StringUtils.hasText(expected)) {
            writeError(response, 503, ErrorCode.OPEN_API_DISABLED.getCode(),
                    "开放接口未配置 API Key");
            return;
        }
        String provided = request.getHeader(HEADER);
        if (!StringUtils.hasText(provided)
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            writeError(response, 401, ErrorCode.OPEN_API_UNAUTHORIZED.getCode(),
                    "X-API-Key 缺失或无效");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, int code, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
    }
}
