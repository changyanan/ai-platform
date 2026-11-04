package com.aiplatform.tool.vo;

import com.aiplatform.tool.entity.ToolExecutionLog;

import java.time.LocalDateTime;

public record ToolExecutionLogVO(
        Long id,
        Long toolId,
        String toolCode,
        Long userId,
        Long agentExecutionId,
        String argumentsJson,
        String resultText,
        boolean success,
        String errorMessage,
        Long durationMs,
        String traceId,
        LocalDateTime createdAt
) {

    public static ToolExecutionLogVO from(ToolExecutionLog log) {
        return new ToolExecutionLogVO(
                log.getId(),
                log.getToolId(),
                log.getToolCode(),
                log.getUserId(),
                log.getAgentExecutionId(),
                log.getArgumentsJson(),
                log.getResultText(),
                Boolean.TRUE.equals(log.getSuccess()),
                log.getErrorMessage(),
                log.getDurationMs(),
                log.getTraceId(),
                log.getCreatedAt()
        );
    }
}
