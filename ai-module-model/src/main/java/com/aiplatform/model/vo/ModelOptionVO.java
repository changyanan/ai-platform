package com.aiplatform.model.vo;

import com.aiplatform.model.enums.ModelType;

import java.util.List;

public record ModelOptionVO(
        Long id,
        String displayName,
        String modelName,
        ModelType modelType,
        String providerName,
        List<String> capabilities,
        boolean available,
        String unavailableReason,
        boolean isDefault,
        double temperature,
        Integer maxTokens,
        Integer contextWindow,
        String connectionStatus
) {
}
