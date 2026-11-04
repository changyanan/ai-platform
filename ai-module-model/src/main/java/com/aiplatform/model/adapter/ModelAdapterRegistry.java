package com.aiplatform.model.adapter;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.model.runtime.ModelRuntime;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 按供应商协议路由到合适的 Adapter。新增供应商时只需实现接口并注册为 Spring Bean。
 */
@Component
@RequiredArgsConstructor
public class ModelAdapterRegistry {

    private final List<ChatModelAdapter> chatAdapters;
    private final List<VisionModelAdapter> visionAdapters;
    private final List<EmbeddingModelAdapter> embeddingAdapters;
    private final List<AsrModelAdapter> asrAdapters;

    public ChatModel chatModel(ModelRuntime runtime) {
        return chatAdapters.stream()
                .filter(adapter -> adapter.supports(runtime.protocol()))
                .findFirst()
                .orElseThrow(() -> unsupported(runtime, "文本"))
                .createChatModel(runtime);
    }

    public ChatModel chatModel(ModelRuntime runtime, List<ChatModelListener> listeners) {
        return chatAdapters.stream()
                .filter(adapter -> adapter.supports(runtime.protocol()))
                .findFirst()
                .orElseThrow(() -> unsupported(runtime, "文本"))
                .createChatModel(runtime, listeners);
    }

    public StreamingChatModel streamingChatModel(ModelRuntime runtime) {
        return chatAdapters.stream()
                .filter(adapter -> adapter.supports(runtime.protocol()))
                .findFirst()
                .orElseThrow(() -> unsupported(runtime, "流式文本"))
                .createStreamingChatModel(runtime);
    }

    public ChatModel visionModel(ModelRuntime runtime) {
        return visionAdapters.stream()
                .filter(adapter -> adapter.supports(runtime.protocol()))
                .findFirst()
                .orElseThrow(() -> unsupported(runtime, "视觉"))
                .createVisionChatModel(runtime);
    }

    public EmbeddingModel embeddingModel(ModelRuntime runtime) {
        return embeddingAdapters.stream()
                .filter(adapter -> adapter.supports(runtime.protocol()))
                .findFirst()
                .orElseThrow(() -> unsupported(runtime, "Embedding"))
                .createEmbeddingModel(runtime);
    }

    public boolean hasAsrAdapter(ModelRuntime runtime) {
        return asrAdapters.stream().anyMatch(adapter -> adapter.supports(runtime.protocol()));
    }

    public AsrModelAdapter asrAdapter(ModelRuntime runtime) {
        return asrAdapters.stream()
                .filter(adapter -> adapter.supports(runtime.protocol()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MULTIMODAL_UNAVAILABLE,
                        "当前供应商暂未接入 ASR 适配器，已明确标记为不支持，不会伪造转录结果"));
    }

    private BusinessException unsupported(ModelRuntime runtime, String capability) {
        return new BusinessException(ErrorCode.MODEL_UNAVAILABLE,
                "供应商协议 " + runtime.protocol() + " 暂不支持" + capability + "能力");
    }
}
