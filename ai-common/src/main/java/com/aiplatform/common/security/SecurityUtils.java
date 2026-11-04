package com.aiplatform.common.security;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 获取当前登录用户。业务层必须基于该用户做数据归属隔离。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录", HttpStatus.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }

    public static String currentUsername() {
        return currentUser().getUsername();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(currentUser().getRole());
    }

    public static boolean hasRole(String role) {
        return role != null && role.equals(currentUser().getRole());
    }
}
