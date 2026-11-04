package com.aiplatform.server.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "ai-platform.jwt")
public class JwtProperties {

    @NotBlank(message = "JWT 密钥不能为空")
    private String secret;

    @Min(value = 5, message = "JWT 有效期至少 5 分钟")
    private long expirationMinutes = 120;

    @NotBlank
    private String issuer = "ai-platform";
}
