package com.aiplatform.prompt.vo;

import java.time.LocalDateTime;
import java.util.List;

public record PromptVO(
        Long id,
        String name,
        String description,
        String category,
        String content,
        List<PromptVariableVO> variables,
        Integer currentVersion,
        Integer publishedVersion,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
