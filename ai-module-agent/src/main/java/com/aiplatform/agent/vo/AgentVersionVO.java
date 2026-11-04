package com.aiplatform.agent.vo;

import com.aiplatform.agent.entity.AgentVersion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgentVersionVO(
        Long id,
        Long agentId,
        Integer version,
        String name,
        String description,
        String systemPrompt,
        Long modelId,
        String toolIds,
        Integer maxToolCalls,
        Integer maxExecutionSeconds,
        BigDecimal maxCost,
        String changeNote,
        LocalDateTime createdAt
) {

    public static AgentVersionVO from(AgentVersion version) {
        return new AgentVersionVO(
                version.getId(),
                version.getAgentId(),
                version.getVersion(),
                version.getName(),
                version.getDescription(),
                version.getSystemPrompt(),
                version.getModelId(),
                version.getToolIds(),
                version.getMaxToolCalls(),
                version.getMaxExecutionSeconds(),
                version.getMaxCost(),
                version.getChangeNote(),
                version.getCreatedAt()
        );
    }
}
