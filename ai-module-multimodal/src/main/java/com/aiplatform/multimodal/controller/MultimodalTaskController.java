package com.aiplatform.multimodal.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.multimodal.service.MultimodalService;
import com.aiplatform.multimodal.vo.MultimodalTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "多模态处理")
@RestController
@RequestMapping("/api/multimodal")
@RequiredArgsConstructor
public class MultimodalTaskController {

    private final MultimodalService multimodalService;

    @Operation(summary = "上传并处理图片/文档/音频")
    @PostMapping(value = "/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MultimodalTaskVO> process(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false) String prompt) {
        return ApiResponse.success(multimodalService.process(file, taskType, modelId, prompt));
    }

    @Operation(summary = "我的多模态任务")
    @GetMapping("/tasks")
    public ApiResponse<List<MultimodalTaskVO>> recent(
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(multimodalService.recent(limit));
    }

    @Operation(summary = "多模态任务详情")
    @GetMapping("/tasks/{id}")
    public ApiResponse<MultimodalTaskVO> get(@PathVariable Long id) {
        return ApiResponse.success(multimodalService.get(id));
    }
}
