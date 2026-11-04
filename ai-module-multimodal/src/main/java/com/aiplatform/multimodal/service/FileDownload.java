package com.aiplatform.multimodal.service;

import org.springframework.core.io.Resource;

public record FileDownload(
        Resource resource,
        String originalName,
        String mimeType,
        long sizeBytes
) {
}
