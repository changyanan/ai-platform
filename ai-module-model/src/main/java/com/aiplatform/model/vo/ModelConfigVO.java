package com.aiplatform.model.vo;

import com.aiplatform.model.enums.ModelType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端模型视图。只包含掩码与 hasApiKey，绝不包含密文或明文 Key。
 */
public record ModelConfigVO(
        Long id,
        Long providerId,
        String providerCode,
        String providerName,
        String displayName,
        String modelName,
        ModelType modelType,
        String apiBaseUrl,
        String effectiveBaseUrl,
        String apiKeyMask,
        boolean hasApiKey,
        BigDecimal temperature,
        Integer maxTokens,
        Integer contextWindow,
        Integer embeddingDimension,
        List<String> capabilities,
        BigDecimal inputPricePer1k,
        BigDecimal outputPricePer1k,
        String currency,
        boolean enabled,
        boolean isDefault,
        String lastTestStatus,
        String lastTestMessage,
        LocalDateTime lastTestAt,
        boolean available,
        String unavailableReason,
        LocalDateTime createdAt
) {
}
