package com.aiplatform.model.adapter;

import com.aiplatform.model.enums.ProviderProtocol;
import com.aiplatform.model.runtime.ModelRuntime;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * OpenAI-compatible 文本与视觉适配器，可同时服务 OpenAI、DeepSeek、
 * DashScope 兼容模式、Ollama 等供应商。
 */
@Component
public class OpenAiCompatibleChatAdapter implements ChatModelAdapter, VisionModelAdapter {

    @Override
    public boolean supports(ProviderProtocol protocol) {
        return protocol == ProviderProtocol.OPENAI_COMPATIBLE;
    }

    @Override
    public ChatModel createChatModel(ModelRuntime runtime) {
        var builder = OpenAiChatModel.builder()
                .baseUrl(normalizeBaseUrl(runtime.baseUrl()))
                .apiKey(apiKey(runtime))
                .modelName(runtime.modelName())
                .temperature(runtime.temperature())
                .timeout(Duration.ofSeconds(runtime.timeoutSeconds()))
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false);
        if (runtime.maxTokens() != null) {
            builder.maxTokens(runtime.maxTokens());
        }
        return builder.build();
    }

    @Override
    public ChatModel createChatModel(ModelRuntime runtime, List<ChatModelListener> listeners) {
        var builder = OpenAiChatModel.builder()
                .baseUrl(normalizeBaseUrl(runtime.baseUrl()))
                .apiKey(apiKey(runtime))
                .modelName(runtime.modelName())
                .temperature(runtime.temperature())
                .timeout(Duration.ofSeconds(runtime.timeoutSeconds()))
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false);
        if (runtime.maxTokens() != null) {
            builder.maxTokens(runtime.maxTokens());
        }
        if (listeners != null && !listeners.isEmpty()) {
            builder.listeners(listeners);
        }
        return builder.build();
    }

    @Override
    public StreamingChatModel createStreamingChatModel(ModelRuntime runtime) {
        var builder = OpenAiStreamingChatModel.builder()
                .baseUrl(normalizeBaseUrl(runtime.baseUrl()))
                .apiKey(apiKey(runtime))
                .modelName(runtime.modelName())
                .temperature(runtime.temperature())
                .timeout(Duration.ofSeconds(runtime.timeoutSeconds()))
                .logRequests(false)
                .logResponses(false);
        if (runtime.maxTokens() != null) {
            builder.maxTokens(runtime.maxTokens());
        }
        return builder.build();
    }

    @Override
    public ChatModel createVisionChatModel(ModelRuntime runtime) {
        // OpenAI-compatible 视觉模型复用 Chat Completions 的消息结构，
        // 图像通过 LangChain4j ImageContent 编码为 data URL。
        return createChatModel(runtime);
    }

    private String apiKey(ModelRuntime runtime) {
        return StringUtils.hasText(runtime.apiKey()) ? runtime.apiKey() : "not-required";
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
