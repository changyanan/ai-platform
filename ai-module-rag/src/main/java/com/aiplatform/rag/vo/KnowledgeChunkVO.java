package com.aiplatform.rag.vo;

import com.aiplatform.rag.entity.KnowledgeChunk;

public record KnowledgeChunkVO(
        Long id,
        Integer chunkIndex,
        String content,
        Integer charCount,
        Integer embeddingDim
) {

    public static KnowledgeChunkVO from(KnowledgeChunk chunk) {
        return new KnowledgeChunkVO(
                chunk.getId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getCharCount(),
                chunk.getEmbeddingDim()
        );
    }
}
