package com.aiplatform.agent.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record AgentRequest(
        @NotBlank(message = "请输入 Agent 名称")
        @Size(max = 100, message = "Agent 名称不能超过 100 个字符")
        String name,

        @Size(max = 500, message = "Agent 描述不能超过 500 个字符")
        String description,

        @NotBlank(message = "请输入 System Prompt")
        @Size(max = 20000, message = "System Prompt 不能超过 20000 个字符")
        String systemPrompt,

        @NotNull(message = "请选择模型")
        Long modelId,

        @Size(max = 20, message = "最多绑定 20 个工具")
        List<Long> toolIds,

        @NotNull(message = "请设置最大工具调用次数")
        @Min(value = 0, message = "最大工具调用次数不能小于 0")
        @Max(value = 20, message = "最大工具调用次数不能超过 20")
        Integer maxToolCalls,

        @NotNull(message = "请设置最大执行时间")
        @Min(value = 10, message = "最大执行时间不能小于 10 秒")
        @Max(value = 300, message = "最大执行时间不能超过 300 秒")
        Integer maxExecutionSeconds,

        @DecimalMin(value = "0.0", message = "成本上限不能为负数")
        BigDecimal maxCost,

        Boolean enabled,

        @Size(max = 500, message = "变更说明不能超过 500 个字符")
        String changeNote
) {
}
