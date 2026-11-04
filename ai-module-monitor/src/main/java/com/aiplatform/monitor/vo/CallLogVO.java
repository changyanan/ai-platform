package com.aiplatform.monitor.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CallLogVO(
        Long id,
        String traceId,
        Long userId,
        Long modelConfigId,
        String providerCode,
        String modelName,
        String requestType,
        Long conversationId,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long latencyMs,
        boolean success,
        String dataSource,
        boolean estimated,
        BigDecimal estimatedCost,
        boolean costKnown,
        String errorMessage,
        String requestSummary,
        LocalDateTime createdAt
) {
}
