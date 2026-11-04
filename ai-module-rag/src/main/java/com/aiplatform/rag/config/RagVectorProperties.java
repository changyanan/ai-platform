package com.aiplatform.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai-platform.rag.postgres")
public class RagVectorProperties {

    private String url = "jdbc:postgresql://127.0.0.1:5432/ai_platform_vector";
    private String username = "ai_platform";
    private String password = "AiPlatform@2026";
    private String table = "ai_chunk_embedding";
    private int poolSize = 6;
}
