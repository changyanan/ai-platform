package com.aiplatform.rag.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.rag.dto.KnowledgeBaseRequest;
import com.aiplatform.rag.service.KnowledgeBaseService;
import com.aiplatform.rag.vo.KnowledgeBaseVO;
import com.aiplatform.rag.vo.KnowledgeChunkVO;
import com.aiplatform.rag.vo.KnowledgeDocumentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "RAG 知识库管理（管理员）")
@RestController
@RequestMapping("/api/admin/rag")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RagAdminController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "知识库列表")
    @GetMapping("/knowledge-bases")
    public ApiResponse<List<KnowledgeBaseVO>> list() {
        return ApiResponse.success(knowledgeBaseService.list());
    }

    @Operation(summary = "知识库详情")
    @GetMapping("/knowledge-bases/{id}")
    public ApiResponse<KnowledgeBaseVO> get(@PathVariable Long id) {
        return ApiResponse.success(knowledgeBaseService.get(id));
    }

    @Operation(summary = "创建知识库")
    @PostMapping("/knowledge-bases")
    public ApiResponse<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseRequest request) {
        return ApiResponse.success(knowledgeBaseService.create(request));
    }

    @Operation(summary = "编辑知识库")
    @PutMapping("/knowledge-bases/{id}")
    public ApiResponse<KnowledgeBaseVO> update(@PathVariable Long id,
                                               @Valid @RequestBody KnowledgeBaseRequest request) {
        return ApiResponse.success(knowledgeBaseService.update(id, request));
    }

    @Operation(summary = "删除知识库及其文档")
    @DeleteMapping("/knowledge-bases/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return ApiResponse.success();
    }

    @Operation(summary = "知识库文档列表")
    @GetMapping("/knowledge-bases/{id}/documents")
    public ApiResponse<List<KnowledgeDocumentVO>> documents(@PathVariable Long id) {
        return ApiResponse.success(knowledgeBaseService.documents(id));
    }

    @Operation(summary = "上传并真实解析、向量化文档")
    @PostMapping(value = "/knowledge-bases/{id}/documents", consumes = "multipart/form-data")
    public ApiResponse<KnowledgeDocumentVO> upload(@PathVariable Long id,
                                                   @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(knowledgeBaseService.uploadDocument(id, file));
    }

    @Operation(summary = "导入内置示例文档（走真实向量化流程）")
    @PostMapping("/knowledge-bases/{id}/demo-document")
    public ApiResponse<KnowledgeDocumentVO> importDemo(@PathVariable Long id) {
        return ApiResponse.success(knowledgeBaseService.importDemoDocument(id));
    }

    @Operation(summary = "重新解析并向量化文档")
    @PostMapping("/documents/{id}/reprocess")
    public ApiResponse<KnowledgeDocumentVO> reprocess(@PathVariable Long id) {
        return ApiResponse.success(knowledgeBaseService.reprocess(id));
    }

    @Operation(summary = "删除知识库文档")
    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long id) {
        knowledgeBaseService.deleteDocument(id);
        return ApiResponse.success();
    }

    @Operation(summary = "查看文档切片")
    @GetMapping("/documents/{id}/chunks")
    public ApiResponse<List<KnowledgeChunkVO>> chunks(@PathVariable Long id) {
        return ApiResponse.success(knowledgeBaseService.chunks(id));
    }
}
