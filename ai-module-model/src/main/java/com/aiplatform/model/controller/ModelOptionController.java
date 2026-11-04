package com.aiplatform.model.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.model.service.ModelConfigService;
import com.aiplatform.model.vo.ModelOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "模型选项")
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelOptionController {

    private final ModelConfigService modelConfigService;

    @Operation(summary = "当前用户可选择的模型（不返回任何密钥信息）")
    @GetMapping("/options")
    public ApiResponse<List<ModelOptionVO>> options() {
        return ApiResponse.success(modelConfigService.options());
    }
}
