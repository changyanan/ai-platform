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
 * 登录用户使用的地址识别接口，供平台页面回填表单。
 * 结果只随响应返回，不写入数据库。
 */
@Tag(name = "地址识别")
@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressExtractionService addressExtractionService;

    @Operation(summary = "从文本识别地址（不落库）")
    @PostMapping("/extract")
    public ApiResponse<AddressExtractionVO> extractText(
            @Valid @RequestBody AddressTextRequest request) {
        return ApiResponse.success(addressExtractionService.extractText(
                request.text(), request.modelId()));
    }

    @Operation(summary = "从图片识别地址（不落库）")
    @PostMapping(value = "/extract/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AddressExtractionVO> extractImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long modelId) {
        return ApiResponse.success(addressExtractionService.extractImage(file, modelId));
    }
}
