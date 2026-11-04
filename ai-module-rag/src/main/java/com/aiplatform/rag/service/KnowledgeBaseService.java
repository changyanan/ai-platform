package com.aiplatform.rag.service;

import com.aiplatform.rag.dto.KnowledgeBaseRequest;
import com.aiplatform.rag.entity.KnowledgeBase;
import com.aiplatform.rag.vo.KnowledgeBaseVO;
import com.aiplatform.rag.vo.KnowledgeChunkVO;
import com.aiplatform.rag.vo.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeBaseService {

    List<KnowledgeBaseVO> list();

    List<KnowledgeBaseVO> listForQuery();

    KnowledgeBaseVO get(Long id);

    KnowledgeBaseVO create(KnowledgeBaseRequest request);

    KnowledgeBaseVO update(Long id, KnowledgeBaseRequest request);

    void delete(Long id);

    KnowledgeBase requireActive(Long id);

    List<KnowledgeDocumentVO> documents(Long knowledgeBaseId);

    KnowledgeDocumentVO uploadDocument(Long knowledgeBaseId, MultipartFile file);

    KnowledgeDocumentVO importDemoDocument(Long knowledgeBaseId);

    KnowledgeDocumentVO reprocess(Long documentId);

    void deleteDocument(Long documentId);

    List<KnowledgeChunkVO> chunks(Long documentId);
}
