package com.aiplatform.tool.dto;

import jakarta.validation.constraints.NotNull;

public record ToolEnabledRequest(
        @NotNull(message = "请指定启用状态")
        Boolean enabled
) {
}
