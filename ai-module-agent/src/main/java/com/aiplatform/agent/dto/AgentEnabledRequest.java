package com.aiplatform.agent.dto;

import jakarta.validation.constraints.NotNull;

public record AgentEnabledRequest(
        @NotNull(message = "请指定启用状态")
        Boolean enabled
) {
}
