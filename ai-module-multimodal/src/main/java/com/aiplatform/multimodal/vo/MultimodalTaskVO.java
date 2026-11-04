package com.aiplatform.multimodal.vo;

import com.aiplatform.multimodal.entity.MultimodalTask;

import java.time.LocalDateTime;

public record MultimodalTaskVO(
        Long id,
        Long fileAssetId,
        String fileName,
        String fileCategory,
        String taskType,
        Long modelConfigId,
        String modelName,
        String status,
        String extractedText,
        String resultText,
        String errorMessage,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        boolean tokenEstimated,
        Long latencyMs,
        LocalDateTime createdAt
) {

    public static MultimodalTaskVO from(MultimodalTask task) {
        return new MultimodalTaskVO(
                task.getId(),
                task.getFileAssetId(),
                task.getFileName(),
                task.getFileCategory(),
                task.getTaskType(),
                task.getModelConfigId(),
                task.getModelName(),
                task.getStatus(),
                task.getExtractedText(),
                task.getResultText(),
                task.getErrorMessage(),
                task.getPromptTokens(),
                task.getCompletionTokens(),
                task.getTotalTokens(),
                Boolean.TRUE.equals(task.getTokenEstimated()),
                task.getLatencyMs(),
                task.getCreatedAt()
        );
    }
}
