package com.aiplatform.rag.vo;

import java.time.LocalDateTime;

public record KnowledgeBaseVO(
        Long id,
        String name,
        String description,
        Long embeddingModelId,
        String embeddingModelName,
        boolean embeddingModelAvailable,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer topK,
        String status,
        boolean preset,
        long documentCount,
        long chunkCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
