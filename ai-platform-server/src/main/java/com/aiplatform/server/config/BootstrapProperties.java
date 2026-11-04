package com.aiplatform.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 演示账号初始化配置。默认密码必须首次登录后修改，生产通过环境变量注入。
 */
@Data
@ConfigurationProperties(prefix = "ai-platform.bootstrap")
public class BootstrapProperties {

    private boolean enabled = true;
    private boolean demoEnabled = false;
    private String adminUsername = "admin";
    private String adminPassword = "Admin@123456";
    private String demoUsername = "demo";
    private String demoPassword = "Demo@123456";
}
