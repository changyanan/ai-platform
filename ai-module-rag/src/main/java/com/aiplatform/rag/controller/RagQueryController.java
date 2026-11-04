package com.aiplatform.rag.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.rag.dto.RagQueryRequest;
import com.aiplatform.rag.service.KnowledgeBaseService;
import com.aiplatform.rag.service.RagQueryService;
import com.aiplatform.rag.vo.KnowledgeBaseVO;
import com.aiplatform.rag.vo.RagQueryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "RAG 知识问答")
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagQueryController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final RagQueryService ragQueryService;

    @Operation(summary = "可用于问答的知识库")
    @GetMapping("/knowledge-bases")
    public ApiResponse<List<KnowledgeBaseVO>> knowledgeBases() {
        return ApiResponse.success(knowledgeBaseService.listForQuery());
    }

    @Operation(summary = "基于真实向量检索的 RAG 问答")
    @PostMapping("/query")
    public ApiResponse<RagQueryVO> query(@Valid @RequestBody RagQueryRequest request) {
        return ApiResponse.success(ragQueryService.query(request));
    }
}
