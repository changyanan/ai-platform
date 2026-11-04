package com.aiplatform.rag.vo;

public record RagSourceVO(
        Long knowledgeBaseId,
        String knowledgeBaseName,
        Long documentId,
        String documentName,
        Integer chunkIndex,
        String content,
        Double score
) {
}
