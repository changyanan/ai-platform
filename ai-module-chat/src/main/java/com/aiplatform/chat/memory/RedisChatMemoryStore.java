package com.aiplatform.chat.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * LangChain4j ChatMemory 的 Redis 存储，用作短期上下文。
 * Redis 不可用时降级为空记忆，业务会从 MySQL 重建上下文，不阻断核心对话。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "ai:chat:memory:";
    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            String json = redisTemplate.opsForValue().get(key(memoryId));
            if (!StringUtils.hasText(json)) {
                return new ArrayList<>();
            }
            return new ArrayList<>(ChatMessageDeserializer.messagesFromJson(json));
        } catch (Exception ex) {
            log.warn("Failed to load Redis chat memory for {}: {}", memoryId, ex.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            deleteMessages(memoryId);
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(memoryId), ChatMessageSerializer.messagesToJson(messages), TTL);
        } catch (Exception ex) {
            log.warn("Failed to persist Redis chat memory for {}: {}", memoryId, ex.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            redisTemplate.delete(key(memoryId));
        } catch (Exception ex) {
            log.warn("Failed to delete Redis chat memory for {}: {}", memoryId, ex.getMessage());
        }
    }

    public void deleteConversationMemory(Long conversationId) {
        deleteMessages(String.valueOf(conversationId));
    }

    private String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }
}
