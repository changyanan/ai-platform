package com.aiplatform.monitor.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.common.api.PageResult;
import com.aiplatform.monitor.dto.CallLogQuery;
import com.aiplatform.monitor.service.MonitorService;
import com.aiplatform.monitor.vo.CallLogVO;
import com.aiplatform.monitor.vo.MonitorOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "调用监控（管理员）")
@RestController
@RequestMapping("/api/admin/monitor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MonitorController {

    private final MonitorService monitorService;

    @Operation(summary = "监控总览：调用、Token、耗时、Agent/Tool/RAG/多模态统计")
    @GetMapping("/overview")
    public ApiResponse<MonitorOverviewVO> overview(
            @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(monitorService.overview(days));
    }

    @Operation(summary = "模型调用日志分页")
    @GetMapping("/calls")
    public ApiResponse<PageResult<CallLogVO>> calls(@Valid CallLogQuery query) {
        return ApiResponse.success(monitorService.calls(query));
    }
}
