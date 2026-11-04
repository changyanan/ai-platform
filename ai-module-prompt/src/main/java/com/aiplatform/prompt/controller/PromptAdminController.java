package com.aiplatform.prompt.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.common.api.PageResult;
import com.aiplatform.prompt.dto.PromptQuery;
import com.aiplatform.prompt.dto.PromptRequest;
import com.aiplatform.prompt.dto.PromptTestRequest;
import com.aiplatform.prompt.service.PromptService;
import com.aiplatform.prompt.vo.PromptTestResultVO;
import com.aiplatform.prompt.vo.PromptVO;
import com.aiplatform.prompt.vo.PromptVersionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Prompt 管理（管理员）")
@RestController
@RequestMapping("/api/admin/prompts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PromptAdminController {

    private final PromptService promptService;

    @Operation(summary = "Prompt 分页列表")
    @GetMapping
    public ApiResponse<PageResult<PromptVO>> page(@Valid PromptQuery query) {
        return ApiResponse.success(promptService.page(query));
    }

    @Operation(summary = "Prompt 分类")
    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.success(promptService.categories());
    }

    @Operation(summary = "Prompt 详情")
    @GetMapping("/{id}")
    public ApiResponse<PromptVO> get(@PathVariable Long id) {
        return ApiResponse.success(promptService.get(id));
    }

    @Operation(summary = "创建 Prompt")
    @PostMapping
    public ApiResponse<PromptVO> create(@Valid @RequestBody PromptRequest request) {
        return ApiResponse.success(promptService.create(request));
    }

    @Operation(summary = "保存新版本")
    @PutMapping("/{id}")
    public ApiResponse<PromptVO> update(@PathVariable Long id,
                                        @Valid @RequestBody PromptRequest request) {
        return ApiResponse.success(promptService.update(id, request));
    }

    @Operation(summary = "版本列表")
    @GetMapping("/{id}/versions")
    public ApiResponse<List<PromptVersionVO>> versions(@PathVariable Long id) {
        return ApiResponse.success(promptService.versions(id));
    }

    @Operation(summary = "发布当前版本")
    @PostMapping("/{id}/publish")
    public ApiResponse<PromptVO> publish(@PathVariable Long id) {
        return ApiResponse.success(promptService.publish(id));
    }

    @Operation(summary = "回滚到指定版本（生成新版本）")
    @PostMapping("/{id}/rollback/{version}")
    public ApiResponse<PromptVO> rollback(@PathVariable Long id, @PathVariable Integer version) {
        return ApiResponse.success(promptService.rollback(id, version));
    }

    @Operation(summary = "真实模型测试 Prompt")
    @PostMapping("/{id}/test")
    public ApiResponse<PromptTestResultVO> test(@PathVariable Long id,
                                                @Valid @RequestBody PromptTestRequest request) {
        return ApiResponse.success(promptService.test(id, request));
    }
}
