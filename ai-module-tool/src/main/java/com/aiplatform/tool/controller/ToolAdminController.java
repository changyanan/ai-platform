package com.aiplatform.tool.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.common.api.PageResult;
import com.aiplatform.tool.dto.ToolDebugRequest;
import com.aiplatform.tool.dto.ToolEnabledRequest;
import com.aiplatform.tool.dto.ToolExecutionQuery;
import com.aiplatform.tool.dto.ToolQuery;
import com.aiplatform.tool.dto.ToolRequest;
import com.aiplatform.tool.service.ToolService;
import com.aiplatform.tool.vo.ToolBeanVO;
import com.aiplatform.tool.vo.ToolDebugResultVO;
import com.aiplatform.tool.vo.ToolExecutionLogVO;
import com.aiplatform.tool.vo.ToolVO;
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

@Tag(name = "Tool 工具管理（管理员）")
@RestController
@RequestMapping("/api/admin/tools")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ToolAdminController {

    private final ToolService toolService;

    @Operation(summary = "工具分页列表")
    @GetMapping
    public ApiResponse<PageResult<ToolVO>> page(@Valid ToolQuery query) {
        return ApiResponse.success(toolService.page(query));
    }

    @Operation(summary = "已注册的 Java 工具 Bean")
    @GetMapping("/beans")
    public ApiResponse<List<ToolBeanVO>> beans() {
        return ApiResponse.success(toolService.beans());
    }

    @Operation(summary = "工具分类")
    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.success(toolService.categories());
    }

    @Operation(summary = "注册工具")
    @PostMapping
    public ApiResponse<ToolVO> create(@Valid @RequestBody ToolRequest request) {
        return ApiResponse.success(toolService.create(request));
    }

    @Operation(summary = "编辑工具元数据")
    @PutMapping("/{id}")
    public ApiResponse<ToolVO> update(@PathVariable Long id,
                                      @Valid @RequestBody ToolRequest request) {
        return ApiResponse.success(toolService.update(id, request));
    }

    @Operation(summary = "启用或停用工具")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> setEnabled(@PathVariable Long id,
                                        @Valid @RequestBody ToolEnabledRequest request) {
        toolService.setEnabled(id, request.enabled());
        return ApiResponse.success();
    }

    @Operation(summary = "调试工具（执行真实 Java 方法）")
    @PostMapping("/{id}/debug")
    public ApiResponse<ToolDebugResultVO> debug(@PathVariable Long id,
                                                @Valid @RequestBody ToolDebugRequest request) {
        return ApiResponse.success(toolService.debug(id, request));
    }

    @Operation(summary = "工具执行记录")
    @GetMapping("/executions")
    public ApiResponse<PageResult<ToolExecutionLogVO>> executions(@Valid ToolExecutionQuery query) {
        return ApiResponse.success(toolService.executions(query));
    }
}
