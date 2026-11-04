package com.aiplatform.server.security;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 首次登录强制修改密码。在 JWT 认证之后执行，服务端阻断而不是只靠前端弹窗。
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_PATHS = List.of(
            "/api/auth/change-password",
            "/api/auth/me",
            "/api/auth/logout",
            "/api/auth/login",
            "/api/health",
            "/actuator/health"
    );

    private final ObjectMapper objectMapper;

    public PasswordChangeRequiredFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof LoginUser loginUser
                && loginUser.isMustChangePassword()
                && !isAllowed(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiResponse.error(ErrorCode.PASSWORD_CHANGE_REQUIRED.getCode(),
                            ErrorCode.PASSWORD_CHANGE_REQUIRED.getDefaultMessage()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(String uri) {
        return ALLOWED_PATHS.stream().anyMatch(uri::equals);
    }
}
