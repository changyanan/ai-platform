package com.aiplatform.model.service;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 一次模型调用的日志快照。显式携带价格快照，避免后续修改模型单价影响历史费用。
 */
@Getter
@Builder
public class ModelCallLogCommand {

    private final String traceId;
    private final Long userId;
    private final Long modelConfigId;
    private final String providerCode;
    private final String modelName;
    private final String requestType;
    private final Long conversationId;
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;
    private final long latencyMs;
    private final boolean success;
    private final Integer httpStatus;
    private final String errorCode;
    private final String errorMessage;
    private final boolean estimated;
    private final BigDecimal inputPricePerThousand;
    private final BigDecimal outputPricePerThousand;
    private final String currency;
    private final String requestSummary;
}
