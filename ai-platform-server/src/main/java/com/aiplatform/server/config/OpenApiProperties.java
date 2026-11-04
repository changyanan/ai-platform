package com.aiplatform.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 面向 ERP 等外部系统的开放接口配置。
 * 默认关闭；生产环境通过环境变量注入随机 API Key。
 */
@Data
@ConfigurationProperties(prefix = "ai-platform.open-api")
public class OpenApiProperties {

    private boolean enabled = false;
    private String key;
}
