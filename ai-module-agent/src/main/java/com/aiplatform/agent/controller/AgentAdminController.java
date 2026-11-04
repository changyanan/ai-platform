package com.aiplatform.agent.controller;

import com.aiplatform.agent.dto.AgentDebugRequest;
import com.aiplatform.agent.dto.AgentEnabledRequest;
import com.aiplatform.agent.dto.AgentExecutionQuery;
import com.aiplatform.agent.dto.AgentQuery;
import com.aiplatform.agent.dto.AgentRequest;
import com.aiplatform.agent.service.AgentRuntimeService;
import com.aiplatform.agent.service.AgentService;
import com.aiplatform.agent.vo.AgentExecutionDetailVO;
import com.aiplatform.agent.vo.AgentExecutionVO;
import com.aiplatform.agent.vo.AgentVO;
import com.aiplatform.agent.vo.AgentVersionVO;
import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.common.api.PageResult;
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

@Tag(name = "Agent 管理（管理员）")
@RestController
@RequestMapping("/api/admin/agents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AgentAdminController {

    private final AgentService agentService;
    private final AgentRuntimeService agentRuntimeService;

    @Operation(summary = "Agent 分页列表")
    @GetMapping
    public ApiResponse<PageResult<AgentVO>> page(@Valid AgentQuery query) {
        return ApiResponse.success(agentService.page(query));
    }

    @Operation(summary = "Agent 详情")
    @GetMapping("/{id}")
    public ApiResponse<AgentVO> get(@PathVariable Long id) {
        return ApiResponse.success(agentService.get(id));
    }

    @Operation(summary = "创建 Agent")
    @PostMapping
    public ApiResponse<AgentVO> create(@Valid @RequestBody AgentRequest request) {
        return ApiResponse.success(agentService.create(request));
    }

    @Operation(summary = "编辑 Agent（生成新版本）")
    @PutMapping("/{id}")
    public ApiResponse<AgentVO> update(@PathVariable Long id,
                                       @Valid @RequestBody AgentRequest request) {
        return ApiResponse.success(agentService.update(id, request));
    }

    @Operation(summary = "删除 Agent（逻辑删除）")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return ApiResponse.success();
    }

    @Operation(summary = "启用或停用 Agent")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> setEnabled(@PathVariable Long id,
                                        @Valid @RequestBody AgentEnabledRequest request) {
        agentService.setEnabled(id, request.enabled());
        return ApiResponse.success();
    }

    @Operation(summary = "Agent 版本历史")
    @GetMapping("/{id}/versions")
    public ApiResponse<List<AgentVersionVO>> versions(@PathVariable Long id) {
        return ApiResponse.success(agentService.versions(id));
    }

    @Operation(summary = "调试 Agent（真实模型 + 真实 Tool 调用）")
    @PostMapping("/{id}/debug")
    public ApiResponse<AgentExecutionDetailVO> debug(@PathVariable Long id,
                                                     @Valid @RequestBody AgentDebugRequest request) {
        return ApiResponse.success(agentRuntimeService.debug(id, request));
    }

    @Operation(summary = "Agent 执行历史")
    @GetMapping("/{id}/executions")
    public ApiResponse<PageResult<AgentExecutionVO>> executions(@PathVariable Long id,
                                                                @Valid AgentExecutionQuery query) {
        query.setAgentId(id);
        return ApiResponse.success(agentRuntimeService.executions(query));
    }
}
