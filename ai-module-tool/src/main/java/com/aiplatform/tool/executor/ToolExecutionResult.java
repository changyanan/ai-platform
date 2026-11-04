package com.aiplatform.tool.executor;

import java.time.LocalDateTime;

public record ToolExecutionResult(
        Long logId,
        Long toolId,
        String toolCode,
        String toolName,
        boolean success,
        String resultText,
        String errorMessage,
        long durationMs,
        LocalDateTime createdAt
) {
}
