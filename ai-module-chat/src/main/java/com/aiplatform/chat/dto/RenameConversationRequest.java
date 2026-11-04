package com.aiplatform.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameConversationRequest(
        @NotBlank(message = "请输入会话标题")
        @Size(max = 200, message = "会话标题不能超过 200 个字符")
        String title
) {
}
