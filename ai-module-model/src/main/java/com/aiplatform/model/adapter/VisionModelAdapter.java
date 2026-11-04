package com.aiplatform.model.adapter;

import com.aiplatform.model.enums.ProviderProtocol;
import com.aiplatform.model.runtime.ModelRuntime;
import dev.langchain4j.model.chat.ChatModel;

/**
 * 视觉模型适配接口。图像通过 LangChain4j ImageContent 传入。
 */
public interface VisionModelAdapter {

    boolean supports(ProviderProtocol protocol);

    ChatModel createVisionChatModel(ModelRuntime runtime);
}
