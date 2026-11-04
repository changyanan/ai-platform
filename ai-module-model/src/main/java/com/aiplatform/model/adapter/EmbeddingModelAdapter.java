package com.aiplatform.model.adapter;

import com.aiplatform.model.enums.ProviderProtocol;
import com.aiplatform.model.runtime.ModelRuntime;
import dev.langchain4j.model.embedding.EmbeddingModel;

public interface EmbeddingModelAdapter {

    boolean supports(ProviderProtocol protocol);

    EmbeddingModel createEmbeddingModel(ModelRuntime runtime);
}
