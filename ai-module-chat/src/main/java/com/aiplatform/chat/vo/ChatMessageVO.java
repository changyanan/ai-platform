package com.aiplatform.chat.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageVO(
        Long id,
        Long conversationId,
        String role,
        String content,
        String status,
        Long modelId,
        String modelName,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        boolean tokenEstimated,
        Long latencyMs,
        List<FileAttachmentVO> attachments,
        LocalDateTime createdAt
) {
}
