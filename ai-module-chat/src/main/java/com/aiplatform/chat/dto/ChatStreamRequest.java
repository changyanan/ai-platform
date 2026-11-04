package com.aiplatform.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatStreamRequest(
        Long conversationId,

        @NotNull(message = "请选择模型")
        Long modelId,

        @NotBlank(message = "请输入对话内容")
        @Size(max = 20000, message = "单条消息不能超过 20000 个字符")
        String content,

        @Size(max = 10, message = "单次最多携带 10 个附件")
        List<Long> fileIds
) {
}
