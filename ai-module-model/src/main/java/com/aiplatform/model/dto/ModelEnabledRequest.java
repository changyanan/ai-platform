package com.aiplatform.model.dto;

import jakarta.validation.constraints.NotNull;

public record ModelEnabledRequest(
        @NotNull(message = "请指定启用状态")
        Boolean enabled
) {
}
