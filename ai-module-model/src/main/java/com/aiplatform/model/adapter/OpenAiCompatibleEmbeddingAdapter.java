package com.aiplatform.model.adapter;

import com.aiplatform.model.enums.ProviderProtocol;
import com.aiplatform.model.runtime.ModelRuntime;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class OpenAiCompatibleEmbeddingAdapter implements EmbeddingModelAdapter {

    @Override
    public boolean supports(ProviderProtocol protocol) {
        return protocol == ProviderProtocol.OPENAI_COMPATIBLE;
    }

    @Override
    public EmbeddingModel createEmbeddingModel(ModelRuntime runtime) {
        var builder = OpenAiEmbeddingModel.builder()
                .baseUrl(normalizeBaseUrl(runtime.baseUrl()))
                .apiKey(StringUtils.hasText(runtime.apiKey()) ? runtime.apiKey() : "not-required")
                .modelName(runtime.modelName())
                .timeout(Duration.ofSeconds(runtime.timeoutSeconds()))
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false);
        if (runtime.embeddingDimension() != null) {
            builder.dimensions(runtime.embeddingDimension());
        }
        return builder.build();
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
