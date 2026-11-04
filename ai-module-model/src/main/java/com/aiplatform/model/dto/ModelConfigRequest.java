package com.aiplatform.model.dto;

import com.aiplatform.model.enums.ModelType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ModelConfigRequest(
        @NotNull(message = "请选择模型供应商")
        Long providerId,

        @NotBlank(message = "请输入模型展示名")
        @Size(max = 100, message = "模型展示名不能超过 100 个字符")
        String displayName,

        @NotBlank(message = "请输入供应商模型名称")
        @Size(max = 160, message = "模型名称不能超过 160 个字符")
        String modelName,

        @NotNull(message = "请选择模型类型")
        ModelType modelType,

        @Size(max = 500, message = "API Base URL 不能超过 500 个字符")
        String apiBaseUrl,

        @Size(max = 2048, message = "API Key 过长")
        String apiKey,

        Boolean clearApiKey,

        @NotNull(message = "请输入 Temperature")
        @DecimalMin(value = "0.0", message = "Temperature 不能小于 0")
        @DecimalMax(value = "2.0", message = "Temperature 不能大于 2")
        BigDecimal temperature,

        @Min(value = 1, message = "最大 Token 必须大于 0")
        @Max(value = 200000, message = "最大 Token 超出范围")
        Integer maxTokens,

        @Min(value = 1, message = "上下文窗口必须大于 0")
        @Max(value = 2000000, message = "上下文窗口超出范围")
        Integer contextWindow,

        @Min(value = 1, message = "Embedding 维度必须大于 0")
        @Max(value = 100000, message = "Embedding 维度超出范围")
        Integer embeddingDimension,

        List<String> capabilities,

        @DecimalMin(value = "0.0", message = "输入单价不能为负数")
        BigDecimal inputPricePer1k,

        @DecimalMin(value = "0.0", message = "输出单价不能为负数")
        BigDecimal outputPricePer1k,

        @Pattern(regexp = "^(CNY|USD|)$", message = "币种仅支持 CNY 或 USD")
        String currency,

        Boolean enabled,

        Boolean isDefault
) {
}
