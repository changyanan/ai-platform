package com.aiplatform.model.vo;

import java.time.LocalDateTime;

public record ModelTestResultVO(
        boolean success,
        String status,
        String message,
        long latencyMs,
        String modelName,
        Integer promptTokens,
        Integer completionTokens,
        LocalDateTime testedAt
) {
}
