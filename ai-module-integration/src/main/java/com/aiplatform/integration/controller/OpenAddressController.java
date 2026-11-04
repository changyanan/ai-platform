package com.aiplatform.integration.controller;

import com.aiplatform.common.api.ApiResponse;
import com.aiplatform.integration.dto.AddressTextRequest;
import com.aiplatform.integration.service.AddressExtractionService;
import com.aiplatform.integration.vo.AddressExtractionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * ERP 服务端调用的开放接口，使用 X-API-Key 认证。
 * 识别结果直接返回给 ERP 页面回填，不写入平台数据库。
 */
@Tag(name = "开放接口 - 地址识别")
@RestController
@RequestMapping("/api/open/v1/address")
@RequiredArgsConstructor
public class OpenAddressController {

    private final AddressExtractionService addressExtractionService;

    @Operation(summary = "从文本提取地址（ERP 服务端调用）")
    @PostMapping("/extract")
    public ApiResponse<AddressExtractionVO> extractText(
            @Valid @RequestBody AddressTextRequest request) {
        return ApiResponse.success(addressExtractionService.extractText(
                request.text(), request.modelId()));
    }

    @Operation(summary = "从图片提取地址（ERP 服务端调用）")
    @PostMapping(value = "/extract/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AddressExtractionVO> extractImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long modelId) {
        return ApiResponse.success(addressExtractionService.extractImage(file, modelId));
    }
}
