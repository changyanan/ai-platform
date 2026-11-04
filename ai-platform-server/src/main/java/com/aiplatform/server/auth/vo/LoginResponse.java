package com.aiplatform.server.auth.vo;

import com.aiplatform.server.user.vo.UserProfileVO;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        boolean mustChangePassword,
        UserProfileVO user
) {
}
