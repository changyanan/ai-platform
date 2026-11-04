package com.aiplatform.server.user.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "请指定启用状态")
        Boolean enabled
) {
}
