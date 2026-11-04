package com.aiplatform.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 流式生成任务状态写入 Redis，供断线排查与后续监控使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTaskStateService {

    private static final String KEY_PREFIX = "ai:chat:task:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public void markStreaming(Long assistantMessageId) {
        write(assistantMessageId, "STREAMING");
    }

    public void markCompleted(Long assistantMessageId) {
        write(assistantMessageId, "COMPLETED");
    }

    public void markFailed(Long assistantMessageId) {
        write(assistantMessageId, "FAILED");
    }

    private void write(Long assistantMessageId, String state) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + assistantMessageId, state, TTL);
        } catch (Exception ex) {
            log.warn("Failed to update chat task state for {}: {}", assistantMessageId, ex.getMessage());
        }
    }
}
