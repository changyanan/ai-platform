package com.aiplatform.model.adapter;

import com.aiplatform.model.enums.ProviderProtocol;
import com.aiplatform.model.runtime.ModelRuntime;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * OpenAI-compatible 视觉模型适配器。图像以 ImageContent 传入，
 * 与文本 ChatModel 使用同一协议，但只在模型声明 VISION 能力时被业务调用。
 */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleVisionAdapter implements VisionModelAdapter {

    private final OpenAiCompatibleChatAdapter chatAdapter;

    @Override
    public boolean supports(ProviderProtocol protocol) {
        return protocol == ProviderProtocol.OPENAI_COMPATIBLE;
    }

    @Override
    public ChatModel createVisionChatModel(ModelRuntime runtime) {
        return chatAdapter.createChatModel(runtime);
    }
}
