package com.aiplatform.tool.vo;

import java.time.LocalDateTime;

public record ToolDebugResultVO(
        Long logId,
        String toolCode,
        String toolName,
        boolean success,
        String result,
        String error,
        long durationMs,
        LocalDateTime createdAt
) {
}
