package com.aiplatform.tool.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ToolDebugRequest(
        @NotNull(message = "请提供调试参数")
        Map<String, Object> arguments
) {
}
