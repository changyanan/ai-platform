package com.aiplatform.rag.service;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.rag.KnowledgeRetrievalPort;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.rag.embedding.RagEmbeddingService;
import com.aiplatform.rag.entity.KnowledgeBase;
import com.aiplatform.rag.entity.KnowledgeChunk;
import com.aiplatform.rag.entity.KnowledgeDocument;
import com.aiplatform.rag.mapper.KnowledgeBaseMapper;
import com.aiplatform.rag.mapper.KnowledgeChunkMapper;
import com.aiplatform.rag.mapper.KnowledgeDocumentMapper;
import com.aiplatform.rag.vector.RagVectorStore;
import com.aiplatform.rag.vo.RagSourceVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService implements KnowledgeRetrievalPort {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final RagVectorStore vectorStore;
    private final RagEmbeddingService embeddingService;
    private final KnowledgeBaseService knowledgeBaseService;

    public List<RagSourceVO> searchSelected(List<Long> knowledgeBaseIds, String query, int topK) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            throw BusinessException.badRequest("请选择知识库");
        }
        Long userId = SecurityUtils.currentUserId();
        List<RagSourceVO> all = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(knowledgeBaseIds)) {
            KnowledgeBase base = knowledgeBaseService.requireActive(id);
            all.addAll(searchOne(base, query, topK, userId));
        }
        return deduplicateAndLimit(all, topK);
    }

    @Override
    public boolean isReady() {
        if (!vectorStore.isReady()) {
            return false;
        }
        Long count = chunkMapper.selectCount(Wrappers.<KnowledgeChunk>lambdaQuery());
        return count != null && count > 0;
    }

    @Override
    public List<RetrievedChunk> searchAll(String query, int topK) {
        if (!vectorStore.isReady()) {
            throw new BusinessException(ErrorCode.RAG_UNAVAILABLE, "PGVector 未就绪");
        }
        Long userId = SecurityUtils.currentUserId();
        List<KnowledgeBase> bases = knowledgeBaseMapper.selectList(Wrappers.<KnowledgeBase>lambdaQuery()
                .eq(KnowledgeBase::getStatus, "ACTIVE")
                .orderByAsc(KnowledgeBase::getId));
        List<RagSourceVO> all = new ArrayList<>();
        String firstError = null;
        for (KnowledgeBase base : bases) {
            Long chunkCount = chunkMapper.selectCount(Wrappers.<KnowledgeChunk>lambdaQuery()
                    .eq(KnowledgeChunk::getKnowledgeBaseId, base.getId()));
            if (chunkCount == null || chunkCount == 0) {
                continue;
            }
            try {
                all.addAll(searchOne(base, query, topK, userId));
            } catch (Exception ex) {
                log.warn("RAG tool search failed for KB {}: {}", base.getId(), ex.getMessage());
                if (firstError == null) {
                    firstError = ex.getMessage();
                }
            }
        }
        if (all.isEmpty() && firstError != null) {
            throw new BusinessException(ErrorCode.RAG_UNAVAILABLE, "知识库检索失败：" + firstError);
        }
        List<RagSourceVO> sources = deduplicateAndLimit(all, topK);
        return sources.stream().map(source -> new RetrievedChunk(
                source.knowledgeBaseId(), source.knowledgeBaseName(),
                source.documentId(), source.documentName(),
                source.chunkIndex(), source.content(), source.score()
        )).toList();
    }

    private List<RagSourceVO> searchOne(KnowledgeBase base, String query, int topK, Long userId) {
        int limit = Math.max(1, Math.min(topK, 20));
        RagEmbeddingService.EmbedResult embedding = embeddingService.embed(
                base.getEmbeddingModelId(), List.of(query), userId,
                "rag query · kb #" + base.getId());
        if (embedding.vectors().isEmpty()) {
            return List.of();
        }
        List<RagVectorStore.ScoredChunkId> scored = vectorStore.search(
                base.getId(), base.getEmbeddingModelId(), embedding.vectors().get(0), limit);
        if (scored.isEmpty()) {
            return List.of();
        }
        List<Long> ids = scored.stream().map(RagVectorStore.ScoredChunkId::chunkId).toList();
        Map<Long, KnowledgeChunk> chunks = chunkMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, Function.identity()));
        List<Long> documentIds = chunks.values().stream().map(KnowledgeChunk::getDocumentId).distinct().toList();
        Map<Long, KnowledgeDocument> documents = documentIds.isEmpty() ? Map.of()
                : documentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));

        List<RagSourceVO> results = new ArrayList<>();
        for (RagVectorStore.ScoredChunkId item : scored) {
            KnowledgeChunk chunk = chunks.get(item.chunkId());
            if (chunk == null) {
                continue;
            }
            KnowledgeDocument document = documents.get(chunk.getDocumentId());
            double score = BigDecimal.valueOf(1.0 - item.distance())
                    .setScale(4, RoundingMode.HALF_UP)
                    .doubleValue();
            results.add(new RagSourceVO(
                    base.getId(),
                    base.getName(),
                    chunk.getDocumentId(),
                    document == null ? "未知文档" : document.getFileName(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    score
            ));
        }
        return results;
    }

    private List<RagSourceVO> deduplicateAndLimit(List<RagSourceVO> sources, int topK) {
        Map<String, RagSourceVO> unique = new LinkedHashMap<>();
        for (RagSourceVO source : sources) {
            unique.putIfAbsent(source.knowledgeBaseId() + ":" + source.documentId()
                    + ":" + source.chunkIndex(), source);
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(RagSourceVO::score,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, topK))
                .toList();
    }
}
