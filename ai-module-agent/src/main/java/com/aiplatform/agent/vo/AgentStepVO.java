package com.aiplatform.agent.vo;

import com.aiplatform.agent.entity.AgentStep;

import java.time.LocalDateTime;

public record AgentStepVO(
        Long id,
        Integer stepIndex,
        String stepType,
        String title,
        String content,
        String toolCode,
        String argumentsJson,
        String toolResult,
        Integer promptTokens,
        Integer completionTokens,
        Long durationMs,
        boolean success,
        LocalDateTime createdAt
) {

    public static AgentStepVO from(AgentStep step) {
        return new AgentStepVO(
                step.getId(),
                step.getStepIndex(),
                step.getStepType(),
                step.getTitle(),
                step.getContent(),
                step.getToolCode(),
                step.getArgumentsJson(),
                step.getToolResult(),
                step.getPromptTokens(),
                step.getCompletionTokens(),
                step.getDurationMs(),
                Boolean.TRUE.equals(step.getSuccess()),
                step.getCreatedAt()
        );
    }
}
