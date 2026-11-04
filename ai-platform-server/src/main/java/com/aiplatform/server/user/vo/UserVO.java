package com.aiplatform.server.user.vo;

import com.aiplatform.server.user.entity.User;

import java.time.LocalDateTime;

public record UserVO(
        Long id,
        String username,
        String displayName,
        String email,
        String role,
        boolean enabled,
        boolean mustChangePassword,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {

    public static UserVO from(User user) {
        return new UserVO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole(),
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
