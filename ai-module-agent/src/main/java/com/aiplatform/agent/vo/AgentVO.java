package com.aiplatform.agent.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AgentVO(
        Long id,
        String name,
        String description,
        String systemPrompt,
        Long modelId,
        String modelName,
        List<Long> toolIds,
        List<ToolBindingVO> tools,
        Integer maxToolCalls,
        Integer maxExecutionSeconds,
        BigDecimal maxCost,
        boolean enabled,
        boolean preset,
        Integer currentVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
