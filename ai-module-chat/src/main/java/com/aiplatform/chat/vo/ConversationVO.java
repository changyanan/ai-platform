package com.aiplatform.chat.vo;

import com.aiplatform.chat.entity.Conversation;

import java.time.LocalDateTime;

public record ConversationVO(
        Long id,
        String title,
        Long modelId,
        String modelName,
        Integer messageCount,
        String lastMessagePreview,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ConversationVO from(Conversation conversation) {
        return new ConversationVO(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getModelId(),
                conversation.getModelName(),
                conversation.getMessageCount(),
                conversation.getLastMessagePreview(),
                conversation.getLastMessageAt(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}
