package com.aiplatform.model.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.common.api.PageResult;
import com.aiplatform.model.dto.ModelConfigQuery;
import com.aiplatform.model.dto.ModelConfigRequest;
import com.aiplatform.model.dto.ModelEnabledRequest;
import com.aiplatform.model.service.ModelConfigService;
import com.aiplatform.model.vo.ModelConfigVO;
import com.aiplatform.model.vo.ModelProviderVO;
import com.aiplatform.model.vo.ModelTestResultVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "模型管理（管理员）")
@RestController
@RequestMapping("/api/admin/models")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ModelAdminController {

    private final ModelConfigService modelConfigService;

    @Operation(summary = "模型配置分页")
    @GetMapping
    public ApiResponse<PageResult<ModelConfigVO>> page(@Valid ModelConfigQuery query) {
        return ApiResponse.success(modelConfigService.page(query));
    }

    @Operation(summary = "可用供应商列表")
    @GetMapping("/providers")
    public ApiResponse<List<ModelProviderVO>> providers() {
        return ApiResponse.success(modelConfigService.providers());
    }

    @Operation(summary = "新增模型配置")
    @PostMapping
    public ApiResponse<ModelConfigVO> create(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.success(modelConfigService.create(request));
    }

    @Operation(summary = "编辑模型配置")
    @PutMapping("/{id}")
    public ApiResponse<ModelConfigVO> update(@PathVariable Long id,
                                             @Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.success(modelConfigService.update(id, request));
    }

    @Operation(summary = "删除模型配置（逻辑删除）")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        modelConfigService.delete(id);
        return ApiResponse.success();
    }

    @Operation(summary = "启用或停用模型")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> setEnabled(@PathVariable Long id,
                                        @Valid @RequestBody ModelEnabledRequest request) {
        modelConfigService.setEnabled(id, request.enabled());
        return ApiResponse.success();
    }

    @Operation(summary = "真实连通性测试")
    @PostMapping("/{id}/test")
    public ApiResponse<ModelTestResultVO> testConnection(@PathVariable Long id) {
        return ApiResponse.success(modelConfigService.testConnection(id));
    }
}
