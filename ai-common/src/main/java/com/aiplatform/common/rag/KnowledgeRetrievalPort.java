package com.aiplatform.common.rag;

import java.util.List;

/**
 * RAG 模块暴露给 Tool / Agent 的检索端口，避免 Tool 反向依赖 RAG 实现。
 * 未装配实现时必须明确报告不可用，禁止返回伪造的检索结果。
 */
public interface KnowledgeRetrievalPort {

    boolean isReady();

    List<RetrievedChunk> searchAll(String query, int topK);

    record RetrievedChunk(
            Long knowledgeBaseId,
            String knowledgeBaseName,
            Long documentId,
            String documentName,
            Integer chunkIndex,
            String content,
            Double score
    ) {
    }
}
