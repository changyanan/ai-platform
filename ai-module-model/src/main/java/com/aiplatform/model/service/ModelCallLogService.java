package com.aiplatform.model.service;

import com.aiplatform.model.entity.ModelCallLog;
import com.aiplatform.model.enums.DataSourceType;
import com.aiplatform.model.mapper.ModelCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCallLogService {

    private final ModelCallLogMapper modelCallLogMapper;

    public void record(ModelCallLogCommand command) {
        try {
            ModelCallLog entity = new ModelCallLog();
            entity.setTraceId(command.getTraceId());
            entity.setUserId(command.getUserId());
            entity.setModelConfigId(command.getModelConfigId());
            entity.setProviderCode(command.getProviderCode());
            entity.setModelName(command.getModelName());
            entity.setRequestType(command.getRequestType());
            entity.setConversationId(command.getConversationId());
            entity.setPromptTokens(command.getPromptTokens());
            entity.setCompletionTokens(command.getCompletionTokens());
            entity.setTotalTokens(command.getTotalTokens());
            entity.setLatencyMs(command.getLatencyMs());
            entity.setSuccess(command.isSuccess());
            entity.setHttpStatus(command.getHttpStatus());
            entity.setErrorCode(trim(command.getErrorCode(), 64));
            entity.setErrorMessage(sanitize(command.getErrorMessage(), 1000));
            entity.setEstimated(command.isEstimated());
            entity.setDataSource(DataSourceType.REAL.name());
            entity.setInputPriceSnapshot(command.getInputPricePerThousand());
            entity.setOutputPriceSnapshot(command.getOutputPricePerThousand());
            entity.setCurrency(command.getCurrency());
            entity.setEstimatedCost(calculateCost(command));
            entity.setRequestSummary(sanitize(command.getRequestSummary(), 500));
            entity.setCreatedAt(LocalDateTime.now());
            modelCallLogMapper.insert(entity);
        } catch (Exception ex) {
            // 监控日志失败不应影响业务响应，但必须在服务端可见。
            log.warn("Failed to persist model call log: {}", ex.getMessage());
        }
    }

    private BigDecimal calculateCost(ModelCallLogCommand command) {
        if (command.getInputPricePerThousand() == null
                || command.getOutputPricePerThousand() == null
                || command.getPromptTokens() == null
                || command.getCompletionTokens() == null) {
            // 价格或 Token 不完整时费用未知，不使用任何猜测值。
            return null;
        }
        BigDecimal promptCost = BigDecimal.valueOf(command.getPromptTokens())
                .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                .multiply(command.getInputPricePerThousand());
        BigDecimal completionCost = BigDecimal.valueOf(command.getCompletionTokens())
                .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                .multiply(command.getOutputPricePerThousand());
        return promptCost.add(completionCost).setScale(8, RoundingMode.HALF_UP);
    }

    private String sanitize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = value
                .replaceAll("(?i)(sk-|api[_-]?key[\"'=:\\s]+)[A-Za-z0-9_\\-]{6,}", "$1***")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return trim(sanitized, maxLength);
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
