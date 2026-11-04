package com.aiplatform.agent.controller;

import com.aiplatform.agent.service.AgentRuntimeService;
import com.aiplatform.agent.vo.AgentExecutionDetailVO;
import com.aiplatform.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent 执行记录（管理员）")
@RestController
@RequestMapping("/api/admin/agent-executions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AgentExecutionController {

    private final AgentRuntimeService agentRuntimeService;

    @Operation(summary = "Agent 执行详情（含步骤）")
    @GetMapping("/{id}")
    public ApiResponse<AgentExecutionDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(agentRuntimeService.executionDetail(id));
    }
}
