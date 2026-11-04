package com.aiplatform.tool.tools;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.rag.KnowledgeRetrievalPort;
import com.aiplatform.tool.executor.PlatformTool;
import com.aiplatform.tool.executor.ToolSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("documentSearchTool")
@RequiredArgsConstructor
public class DocumentSearchTool implements PlatformTool {

    private final ObjectProvider<KnowledgeRetrievalPort> retrievalPortProvider;
    private final ObjectMapper objectMapper;

    @Override
    public String execute(Map<String, Object> arguments) {
        String query = ToolSupport.requireString(arguments, "query", "检索问题");
        int topK = 3;
        Object rawTopK = arguments.get("topK");
        if (rawTopK instanceof Number number) {
            topK = Math.max(1, Math.min(10, number.intValue()));
        }

        KnowledgeRetrievalPort port = retrievalPortProvider.getIfAvailable();
        if (port == null || !port.isReady()) {
            throw new BusinessException(ErrorCode.RAG_UNAVAILABLE,
                    "知识库检索尚未就绪，请先在 RAG 知识库中完成文档向量化");
        }

        List<KnowledgeRetrievalPort.RetrievedChunk> chunks = port.searchAll(query, topK);
        List<Map<String, Object>> sources = chunks.stream().map(chunk -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("knowledgeBaseId", chunk.knowledgeBaseId());
            item.put("knowledgeBaseName", chunk.knowledgeBaseName());
            item.put("documentId", chunk.documentId());
            item.put("documentName", chunk.documentName());
            item.put("chunkIndex", chunk.chunkIndex());
            item.put("score", chunk.score());
            item.put("content", chunk.content());
            return item;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("count", sources.size());
        result.put("sources", sources);
        result.put("dataSource", "RAG_VECTOR");
        result.put("note", sources.isEmpty() ? "知识库未检索到相关内容，不得编造来源" : "来源来自本项目向量知识库");
        return ToolSupport.toJson(objectMapper, result);
    }
}
