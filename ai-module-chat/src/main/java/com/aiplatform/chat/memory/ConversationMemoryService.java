package com.aiplatform.chat.memory;

import com.aiplatform.chat.config.ChatProperties;
import com.aiplatform.chat.enums.MessageRole;
import com.aiplatform.chat.enums.MessageStatus;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * LangChain4j ChatMemory 生命周期管理：优先从 Redis 恢复，Redis 为空时由 MySQL 历史重建。
 * 只向短期记忆写入文本，图片字节不会进入 Redis。
 */
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private final RedisChatMemoryStore redisChatMemoryStore;
    private final ChatProperties chatProperties;

    public ChatMemory loadMemory(Long conversationId,
                                 List<com.aiplatform.chat.entity.ChatMessage> persistedHistory) {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id(String.valueOf(conversationId))
                .maxMessages(chatProperties.getMaxContextMessages())
                .chatMemoryStore(redisChatMemoryStore)
                .build();

        if (memory.messages().isEmpty() && persistedHistory != null) {
            for (com.aiplatform.chat.entity.ChatMessage entity : persistedHistory) {
                if (MessageRole.USER.name().equals(entity.getRole()) && StringUtils.hasText(entity.getContent())) {
                    memory.add(UserMessage.from(entity.getContent()));
                } else if (MessageRole.ASSISTANT.name().equals(entity.getRole())
                        && MessageStatus.COMPLETED.name().equals(entity.getStatus())
                        && StringUtils.hasText(entity.getContent())) {
                    memory.add(AiMessage.from(entity.getContent()));
                }
            }
        }
        return memory;
    }

    public void appendUser(ChatMemory memory, String content) {
        if (StringUtils.hasText(content)) {
            memory.add(UserMessage.from(content));
        }
    }

    public void appendAssistant(ChatMemory memory, String content) {
        if (StringUtils.hasText(content)) {
            memory.add(AiMessage.from(content));
        }
    }

    public void clear(Long conversationId) {
        redisChatMemoryStore.deleteConversationMemory(conversationId);
    }
}
