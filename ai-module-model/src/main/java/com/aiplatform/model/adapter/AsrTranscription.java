package com.aiplatform.model.adapter;

public record AsrTranscription(
        String text,
        String language,
        Integer durationSeconds
) {
}
