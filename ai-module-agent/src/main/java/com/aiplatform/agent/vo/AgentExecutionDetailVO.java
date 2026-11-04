package com.aiplatform.agent.vo;

import java.util.List;

public record AgentExecutionDetailVO(
        AgentExecutionVO execution,
        List<AgentStepVO> steps
) {
}
