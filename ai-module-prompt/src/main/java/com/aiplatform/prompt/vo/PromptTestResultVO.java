package com.aiplatform.prompt.vo;

public record PromptTestResultVO(
        String renderedPrompt,
        String output,
        String modelName,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        boolean tokenEstimated,
        Long latencyMs
) {
}
