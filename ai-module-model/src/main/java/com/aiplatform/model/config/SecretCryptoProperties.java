package com.aiplatform.model.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "ai-platform.crypto")
public class SecretCryptoProperties {

    @NotBlank(message = "AI_PLATFORM_MASTER_KEY 不能为空")
    private String masterKey;
}
