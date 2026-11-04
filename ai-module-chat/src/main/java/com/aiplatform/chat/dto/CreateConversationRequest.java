package com.aiplatform.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @NotNull(message = "请选择模型")
        Long modelId,

        @Size(max = 200, message = "会话标题不能超过 200 个字符")
        String title
) {
}
