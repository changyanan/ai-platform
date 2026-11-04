package com.aiplatform.multimodal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@Data
@ConfigurationProperties(prefix = "ai-platform.upload")
public class UploadProperties {

    private DataSize maxImageSize = DataSize.ofMegabytes(15);
    private DataSize maxDocumentSize = DataSize.ofMegabytes(20);
    private DataSize maxAudioSize = DataSize.ofMegabytes(25);
}
