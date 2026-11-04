package com.aiplatform.agent.vo;

import com.aiplatform.agent.entity.AgentExecution;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgentExecutionVO(
        Long id,
        Long agentId,
        String agentName,
        Integer agentVersion,
        String modelName,
        Long userId,
        String inputText,
        String outputText,
        String status,
        Integer toolCallCount,
        Integer modelCallCount,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        boolean tokenEstimated,
        BigDecimal estimatedCost,
        Long latencyMs,
        String errorMessage,
        String traceId,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {

    public static AgentExecutionVO from(AgentExecution execution) {
        return new AgentExecutionVO(
                execution.getId(),
                execution.getAgentId(),
                execution.getAgentName(),
                execution.getAgentVersion(),
                execution.getModelName(),
                execution.getUserId(),
                execution.getInputText(),
                execution.getOutputText(),
                execution.getStatus(),
                execution.getToolCallCount(),
                execution.getModelCallCount(),
                execution.getPromptTokens(),
                execution.getCompletionTokens(),
                execution.getTotalTokens(),
                Boolean.TRUE.equals(execution.getTokenEstimated()),
                execution.getEstimatedCost(),
                execution.getLatencyMs(),
                execution.getErrorMessage(),
                execution.getTraceId(),
                execution.getCreatedAt(),
                execution.getFinishedAt()
        );
    }
}
