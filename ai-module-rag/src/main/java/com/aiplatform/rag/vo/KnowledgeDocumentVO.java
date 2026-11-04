package com.aiplatform.rag.vo;

import com.aiplatform.rag.entity.KnowledgeDocument;

import java.time.LocalDateTime;

public record KnowledgeDocumentVO(
        Long id,
        Long knowledgeBaseId,
        Long fileAssetId,
        String fileName,
        String extension,
        String sourceType,
        Long sizeBytes,
        Integer charCount,
        Integer chunkCount,
        String status,
        String errorMessage,
        String textPreview,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {

    public static KnowledgeDocumentVO from(KnowledgeDocument document) {
        String text = document.getExtractedText();
        String preview = text == null ? null : text.substring(0, Math.min(180, text.length()));
        return new KnowledgeDocumentVO(
                document.getId(),
                document.getKnowledgeBaseId(),
                document.getFileAssetId(),
                document.getFileName(),
                document.getExtension(),
                document.getSourceType(),
                document.getSizeBytes(),
                document.getCharCount(),
                document.getChunkCount(),
                document.getStatus(),
                document.getErrorMessage(),
                preview,
                document.getCreatedAt(),
                document.getProcessedAt()
        );
    }
}
