package com.aiplatform.agent.vo;

public record ToolBindingVO(
        Long id,
        String toolCode,
        String name,
        boolean enabled
) {
}
