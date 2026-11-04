package com.aiplatform.model.adapter;

import com.aiplatform.model.enums.ProviderProtocol;
import com.aiplatform.model.runtime.ModelRuntime;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import java.util.List;

/**
 * 文本模型供应商适配接口。业务层不直接构造供应商 SDK 对象。
 */
public interface ChatModelAdapter {

    boolean supports(ProviderProtocol protocol);

    ChatModel createChatModel(ModelRuntime runtime);

    /**
     * 支持监听器的构造方式，Agent 用它采集每一步模型请求/响应与 Token。
     */
    default ChatModel createChatModel(ModelRuntime runtime, List<ChatModelListener> listeners) {
        return createChatModel(runtime);
    }

    StreamingChatModel createStreamingChatModel(ModelRuntime runtime);
}
