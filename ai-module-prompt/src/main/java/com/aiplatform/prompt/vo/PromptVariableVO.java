package com.aiplatform.prompt.vo;

public record PromptVariableVO(
        String name,
        String label,
        boolean required,
        String defaultValue
) {
}
