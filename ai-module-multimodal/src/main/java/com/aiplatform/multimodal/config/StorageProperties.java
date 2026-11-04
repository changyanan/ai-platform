package com.aiplatform.multimodal.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "ai-platform.storage")
public class StorageProperties {

    @NotBlank(message = "文件存储根目录不能为空")
    private String root = "./data/uploads";
}
