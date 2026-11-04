package com.aiplatform.rag.service.impl;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.model.entity.ModelConfig;
import com.aiplatform.model.entity.ModelProvider;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.mapper.ModelProviderMapper;
import com.aiplatform.model.service.ModelRuntimeFactory;
import com.aiplatform.multimodal.entity.FileAsset;
import com.aiplatform.multimodal.mapper.FileAssetMapper;
import com.aiplatform.multimodal.service.FileAssetService;
import com.aiplatform.multimodal.vo.FileAssetVO;
import com.aiplatform.rag.demo.DemoKnowledgeDocuments;
import com.aiplatform.rag.document.TextChunker;
import com.aiplatform.multimodal.document.DocumentTextExtractor;
import com.aiplatform.rag.dto.KnowledgeBaseRequest;
import com.aiplatform.rag.embedding.RagEmbeddingService;
import com.aiplatform.rag.entity.KnowledgeBase;
import com.aiplatform.rag.entity.KnowledgeChunk;
import com.aiplatform.rag.entity.KnowledgeDocument;
import com.aiplatform.rag.mapper.KnowledgeBaseMapper;
import com.aiplatform.rag.mapper.KnowledgeChunkMapper;
import com.aiplatform.rag.mapper.KnowledgeDocumentMapper;
import com.aiplatform.rag.service.KnowledgeBaseService;
import com.aiplatform.rag.vector.RagVectorStore;
import com.aiplatform.rag.vo.KnowledgeBaseVO;
import com.aiplatform.rag.vo.KnowledgeChunkVO;
import com.aiplatform.rag.vo.KnowledgeDocumentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final FileAssetMapper fileAssetMapper;
    private final FileAssetService fileAssetService;
    private final RagVectorStore vectorStore;
    private final RagEmbeddingService embeddingService;
    private final DocumentTextExtractor textExtractor;
    private final TextChunker textChunker;
    private final ModelRuntimeFactory modelRuntimeFactory;
    private final ModelProviderMapper modelProviderMapper;

    @Override
    public List<KnowledgeBaseVO> list() {
        return knowledgeBaseMapper.selectList(Wrappers.<KnowledgeBase>lambdaQuery()
                        .orderByAsc(KnowledgeBase::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public List<KnowledgeBaseVO> listForQuery() {
        return knowledgeBaseMapper.selectList(Wrappers.<KnowledgeBase>lambdaQuery()
                        .eq(KnowledgeBase::getStatus, "ACTIVE")
                        .orderByAsc(KnowledgeBase::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    public KnowledgeBaseVO get(Long id) {
        return toVO(requireKnowledgeBase(id));
    }

    @Override
    @Transactional
    public KnowledgeBaseVO create(KnowledgeBaseRequest request) {
        validateRequest(request);
        ensureNameAvailable(request.name().trim(), null);
        KnowledgeBase base = new KnowledgeBase();
        base.setName(request.name().trim());
        base.setDescription(normalizeOptional(request.description()));
        base.setEmbeddingModelId(request.embeddingModelId());
        base.setChunkSize(request.chunkSize());
        base.setChunkOverlap(request.chunkOverlap());
        base.setTopK(request.topK());
        base.setStatus(normalizeStatus(request.status()));
        base.setIsPreset(false);
        base.setCreatedBy(SecurityUtils.currentUserId());
        knowledgeBaseMapper.insert(base);
        log.info("Knowledge base {} created by {}", base.getId(), SecurityUtils.currentUsername());
        return toVO(knowledgeBaseMapper.selectById(base.getId()));
    }

    @Override
    @Transactional
    public KnowledgeBaseVO update(Long id, KnowledgeBaseRequest request) {
        KnowledgeBase existing = requireKnowledgeBase(id);
        validateRequest(request);
        ensureNameAvailable(request.name().trim(), id);
        long chunkCount = chunkMapper.selectCount(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getKnowledgeBaseId, id));
        if (!existing.getEmbeddingModelId().equals(request.embeddingModelId()) && chunkCount > 0) {
            throw BusinessException.conflict("知识库已有向量数据，切换 Embedding 模型前请先删除或重新处理文档");
        }
        knowledgeBaseMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id)
                .set(KnowledgeBase::getName, request.name().trim())
                .set(KnowledgeBase::getDescription, normalizeOptional(request.description()))
                .set(KnowledgeBase::getEmbeddingModelId, request.embeddingModelId())
                .set(KnowledgeBase::getChunkSize, request.chunkSize())
                .set(KnowledgeBase::getChunkOverlap, request.chunkOverlap())
                .set(KnowledgeBase::getTopK, request.topK())
                .set(KnowledgeBase::getStatus, normalizeStatus(request.status())));
        return toVO(knowledgeBaseMapper.selectById(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        KnowledgeBase base = requireKnowledgeBase(id);
        List<KnowledgeDocument> documents = documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getKnowledgeBaseId, id));
        for (KnowledgeDocument document : documents) {
            vectorStore.deleteByDocument(document.getId());
            chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery()
                    .eq(KnowledgeChunk::getDocumentId, document.getId()));
        }
        documentMapper.delete(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getKnowledgeBaseId, id));
        vectorStore.deleteByKnowledgeBase(id);
        knowledgeBaseMapper.deleteById(id);
        log.info("Knowledge base {} ({}) deleted by {}", id, base.getName(), SecurityUtils.currentUsername());
    }

    @Override
    public KnowledgeBase requireActive(Long id) {
        KnowledgeBase base = requireKnowledgeBase(id);
        if (!"ACTIVE".equals(base.getStatus())) {
            throw BusinessException.badRequest("知识库「" + base.getName() + "」已停用");
        }
        vectorStore.ensureReady();
        return base;
    }

    @Override
    public List<KnowledgeDocumentVO> documents(Long knowledgeBaseId) {
        requireKnowledgeBase(knowledgeBaseId);
        return documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                        .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                        .orderByDesc(KnowledgeDocument::getId))
                .stream().map(KnowledgeDocumentVO::from).toList();
    }

    @Override
    public KnowledgeDocumentVO uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        KnowledgeBase base = requireActive(knowledgeBaseId);
        FileAssetVO uploaded = fileAssetService.upload(file);
        KnowledgeDocument document = newDocument(base, uploaded.originalName(),
                uploaded.extension(), "UPLOAD", uploaded.sizeBytes());
        document.setFileAssetId(uploaded.id());
        documentMapper.insert(document);
        try {
            processDocument(base, document);
        } catch (RuntimeException ex) {
            log.warn("RAG document {} processing failed: {}", document.getId(), ex.getMessage());
            throw ex;
        }
        return KnowledgeDocumentVO.from(documentMapper.selectById(document.getId()));
    }

    @Override
    public KnowledgeDocumentVO importDemoDocument(Long knowledgeBaseId) {
        KnowledgeBase base = requireActive(knowledgeBaseId);
        KnowledgeDocument existing = documentMapper.selectOne(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeDocument::getSourceType, "DEMO")
                .orderByDesc(KnowledgeDocument::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            if ("FAILED".equals(existing.getStatus())) {
                processDocument(base, existing);
                return KnowledgeDocumentVO.from(documentMapper.selectById(existing.getId()));
            }
            return KnowledgeDocumentVO.from(existing);
        }

        boolean service = base.getName().contains("客服") || base.getName().contains("客户");
        String fileName = service ? DemoKnowledgeDocuments.SERVICE_NAME : DemoKnowledgeDocuments.PRODUCT_NAME;
        String content = service ? DemoKnowledgeDocuments.SERVICE_CONTENT : DemoKnowledgeDocuments.PRODUCT_CONTENT;
        KnowledgeDocument document = newDocument(base, fileName, "md", "DEMO",
                (long) content.getBytes(StandardCharsets.UTF_8).length);
        document.setExtractedText(content);
        document.setCharCount(content.length());
        documentMapper.insert(document);
        processDocument(base, document);
        return KnowledgeDocumentVO.from(documentMapper.selectById(document.getId()));
    }

    @Override
    public KnowledgeDocumentVO reprocess(Long documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        KnowledgeBase base = requireActive(document.getKnowledgeBaseId());
        processDocument(base, document);
        return KnowledgeDocumentVO.from(documentMapper.selectById(documentId));
    }

    @Override
    public void deleteDocument(Long documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        vectorStore.deleteByDocument(documentId);
        chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getDocumentId, documentId));
        documentMapper.deleteById(documentId);
        log.info("RAG document {} ({}) deleted by {}", documentId, document.getFileName(),
                SecurityUtils.currentUsername());
    }

    @Override
    public List<KnowledgeChunkVO> chunks(Long documentId) {
        requireDocument(documentId);
        return chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getDocumentId, documentId)
                        .orderByAsc(KnowledgeChunk::getChunkIndex))
                .stream().map(KnowledgeChunkVO::from).toList();
    }

    private void processDocument(KnowledgeBase base, KnowledgeDocument document) {
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .set(KnowledgeDocument::getStatus, "PROCESSING")
                .set(KnowledgeDocument::getErrorMessage, null));
        try {
            String text = document.getExtractedText();
            if (!StringUtils.hasText(text)) {
                text = extractFromFile(document);
            }
            if (!StringUtils.hasText(text)) {
                throw new BusinessException(ErrorCode.RAG_UNAVAILABLE, "文档没有可用的文本内容");
            }
            int charCount = text.length();
            List<String> chunks = textChunker.chunk(text, base.getChunkSize(), base.getChunkOverlap());
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.RAG_UNAVAILABLE, "文档切片为空");
            }
            RagEmbeddingService.EmbedResult embedResult = embeddingService.embed(
                    base.getEmbeddingModelId(), chunks, SecurityUtils.currentUserId(),
                    "rag document #" + document.getId() + " · " + document.getFileName());

            vectorStore.deleteByDocument(document.getId());
            chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery()
                    .eq(KnowledgeChunk::getDocumentId, document.getId()));

            List<KnowledgeChunk> entities = new ArrayList<>(chunks.size());
            LocalDateTime now = LocalDateTime.now();
            for (int index = 0; index < chunks.size(); index++) {
                String content = chunks.get(index);
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setKnowledgeBaseId(base.getId());
                chunk.setDocumentId(document.getId());
                chunk.setChunkIndex(index);
                chunk.setContent(content);
                chunk.setCharCount(content.length());
                chunk.setEmbeddingModelId(base.getEmbeddingModelId());
                chunk.setEmbeddingDim(embedResult.dimension());
                chunk.setCreatedAt(now);
                chunkMapper.insert(chunk);
                entities.add(chunk);
            }

            List<RagVectorStore.VectorRecord> records = new ArrayList<>(entities.size());
            for (int index = 0; index < entities.size(); index++) {
                KnowledgeChunk chunk = entities.get(index);
                records.add(new RagVectorStore.VectorRecord(chunk.getId(), base.getId(), document.getId(),
                        base.getEmbeddingModelId(), embedResult.vectors().get(index)));
            }
            vectorStore.upsert(records);

            documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getId, document.getId())
                    .set(KnowledgeDocument::getExtractedText, text)
                    .set(KnowledgeDocument::getCharCount, charCount)
                    .set(KnowledgeDocument::getChunkCount, chunks.size())
                    .set(KnowledgeDocument::getStatus, "READY")
                    .set(KnowledgeDocument::getErrorMessage, null)
                    .set(KnowledgeDocument::getProcessedAt, LocalDateTime.now()));
            document.setExtractedText(text);
            document.setCharCount(charCount);
            document.setChunkCount(chunks.size());
            document.setStatus("READY");
            log.info("RAG document {} processed: {} chars, {} chunks, dim {}",
                    document.getId(), charCount, chunks.size(), embedResult.dimension());
        } catch (Exception ex) {
            String message = safeError(ex);
            try {
                vectorStore.deleteByDocument(document.getId());
                chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery()
                        .eq(KnowledgeChunk::getDocumentId, document.getId()));
                documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getId, document.getId())
                        .set(KnowledgeDocument::getChunkCount, 0)
                        .set(KnowledgeDocument::getStatus, "FAILED")
                        .set(KnowledgeDocument::getErrorMessage, message));
            } catch (Exception updateError) {
                log.warn("Failed to mark document {} as failed: {}", document.getId(), updateError.getMessage());
            }
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.RAG_UNAVAILABLE, "文档向量化失败：" + message);
        }
    }

    private String extractFromFile(KnowledgeDocument document) {
        if (document.getFileAssetId() == null) {
            throw new BusinessException(ErrorCode.RAG_UNAVAILABLE, "文档没有可解析的源文件");
        }
        FileAsset asset = fileAssetMapper.selectById(document.getFileAssetId());
        if (asset == null) {
            throw new BusinessException(ErrorCode.RAG_UNAVAILABLE, "源文件不存在或已删除");
        }
        byte[] bytes = fileAssetService.readBytes(asset);
        return textExtractor.extract(asset.getExtension(), bytes);
    }

    private KnowledgeDocument newDocument(KnowledgeBase base, String fileName, String extension,
                                          String sourceType, Long sizeBytes) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setKnowledgeBaseId(base.getId());
        document.setFileName(fileName);
        document.setExtension(extension);
        document.setSourceType(sourceType);
        document.setSizeBytes(sizeBytes);
        document.setChunkCount(0);
        document.setStatus("PROCESSING");
        document.setCreatedBy(SecurityUtils.currentUserId());
        return document;
    }

    private void validateRequest(KnowledgeBaseRequest request) {
        ModelConfig model = modelRuntimeFactory.findConfig(request.embeddingModelId());
        if (model == null) {
            throw BusinessException.badRequest("Embedding 模型不存在或已删除");
        }
        if (!ModelType.EMBEDDING.name().equals(model.getModelType())) {
            throw BusinessException.badRequest("知识库只能绑定 Embedding 类型模型");
        }
        if (request.chunkOverlap() >= request.chunkSize()) {
            throw BusinessException.badRequest("切片重叠必须小于切片大小");
        }
    }

    private void ensureNameAvailable(String name, Long excludeId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = Wrappers.<KnowledgeBase>lambdaQuery()
                .eq(KnowledgeBase::getName, name);
        if (excludeId != null) {
            wrapper.ne(KnowledgeBase::getId, excludeId);
        }
        if (knowledgeBaseMapper.selectCount(wrapper) > 0) {
            throw BusinessException.conflict("知识库名称已存在");
        }
    }

    private KnowledgeBase requireKnowledgeBase(Long id) {
        KnowledgeBase base = knowledgeBaseMapper.selectById(id);
        if (base == null) {
            throw BusinessException.notFound("知识库不存在");
        }
        return base;
    }

    private KnowledgeDocument requireDocument(Long id) {
        KnowledgeDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw BusinessException.notFound("知识库文档不存在");
        }
        return document;
    }

    private KnowledgeBaseVO toVO(KnowledgeBase base) {
        ModelConfig model = modelRuntimeFactory.findConfig(base.getEmbeddingModelId());
        boolean available = false;
        String modelName = "模型已删除";
        if (model != null) {
            modelName = model.getDisplayName();
            ModelProvider provider = modelProviderMapper.selectById(model.getProviderId());
            available = provider != null && modelRuntimeFactory.isAvailable(model, provider);
        }
        long documentCount = documentMapper.selectCount(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getKnowledgeBaseId, base.getId()));
        long chunkCount = chunkMapper.selectCount(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getKnowledgeBaseId, base.getId()));
        return new KnowledgeBaseVO(
                base.getId(),
                base.getName(),
                base.getDescription(),
                base.getEmbeddingModelId(),
                modelName,
                available,
                base.getChunkSize(),
                base.getChunkOverlap(),
                base.getTopK(),
                base.getStatus(),
                Boolean.TRUE.equals(base.getIsPreset()),
                documentCount,
                chunkCount,
                base.getCreatedAt(),
                base.getUpdatedAt()
        );
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "ACTIVE";
        }
        String value = status.trim().toUpperCase();
        if (!"ACTIVE".equals(value) && !"DISABLED".equals(value)) {
            throw BusinessException.badRequest("知识库状态不合法");
        }
        return value;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() > 1900 ? message.substring(0, 1900) : message;
    }
}
