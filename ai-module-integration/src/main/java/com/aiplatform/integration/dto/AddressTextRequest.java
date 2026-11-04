package com.aiplatform.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressTextRequest(
        @NotBlank(message = "请输入需要识别的地址文本")
        @Size(max = 20000, message = "文本不能超过 20000 个字符")
        String text,
        Long modelId
) {
}
