package com.aiplatform.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentDebugRequest(
        @NotBlank(message = "请输入调试内容")
        @Size(max = 10000, message = "调试输入不能超过 10000 个字符")
        String input
) {
}
