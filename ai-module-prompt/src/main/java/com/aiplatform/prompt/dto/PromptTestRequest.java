package com.aiplatform.prompt.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record PromptTestRequest(
        @NotNull(message = "请选择测试模型")
        Long modelId,
        Map<String, String> variables
) {
}
