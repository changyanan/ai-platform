package com.aiplatform.chat.service;

import com.aiplatform.model.runtime.ModelRuntime;
import com.aiplatform.multimodal.entity.FileAsset;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;

/**
 * SSE 异步执行的内部上下文。只存在于服务端内存，不序列化到 API。
 */
public record ChatStreamContext(
        Long userId,
        Long conversationId,
        Long userMessageId,
        Long assistantMessageId,
        String conversationTitle,
        String userContent,
        String memoryUserText,
        ModelRuntime runtime,
        ChatMemory memory,
        List<FileAsset> attachments,
        String traceId
) {

    @Override
    public String toString() {
        return "ChatStreamContext[conversationId=" + conversationId
                + ", assistantMessageId=" + assistantMessageId
                + ", model=" + (runtime == null ? null : runtime.displayName())
                + ", attachments=" + (attachments == null ? 0 : attachments.size()) + "]";
    }
}
