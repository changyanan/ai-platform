package com.aiplatform.tool.vo;

import com.aiplatform.tool.entity.AiTool;

import java.time.LocalDateTime;

public record ToolVO(
        Long id,
        String toolCode,
        String name,
        String description,
        String parametersSchema,
        String exampleArguments,
        String category,
        String beanName,
        boolean enabled,
        boolean builtIn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ToolVO from(AiTool tool) {
        return new ToolVO(
                tool.getId(),
                tool.getToolCode(),
                tool.getName(),
                tool.getDescription(),
                tool.getParametersSchema(),
                tool.getExampleArguments(),
                tool.getCategory(),
                tool.getBeanName(),
                Boolean.TRUE.equals(tool.getEnabled()),
                Boolean.TRUE.equals(tool.getBuiltIn()),
                tool.getCreatedAt(),
                tool.getUpdatedAt()
        );
    }
}
