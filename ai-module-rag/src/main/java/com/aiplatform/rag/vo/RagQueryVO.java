package com.aiplatform.rag.vo;

import java.util.List;

public record RagQueryVO(
        String question,
        String answer,
        String modelName,
        List<RagSourceVO> sources,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        boolean tokenEstimated,
        Long latencyMs,
        boolean hasContext
) {
}
