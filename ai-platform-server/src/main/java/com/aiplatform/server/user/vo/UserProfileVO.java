package com.aiplatform.server.user.vo;

import com.aiplatform.server.security.UserPrincipal;
import com.aiplatform.server.user.entity.User;

public record UserProfileVO(
        Long id,
        String username,
        String displayName,
        String email,
        String role,
        boolean enabled,
        boolean mustChangePassword
) {

    public static UserProfileVO from(User user) {
        return new UserProfileVO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole(),
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getMustChangePassword())
        );
    }

    public static UserProfileVO from(UserPrincipal principal) {
        return new UserProfileVO(
                principal.getId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getEmail(),
                principal.getRole(),
                principal.isEnabled(),
                principal.isMustChangePassword()
        );
    }
}
