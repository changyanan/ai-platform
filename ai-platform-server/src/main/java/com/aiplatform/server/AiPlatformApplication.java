package com.aiplatform.server;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 模块化单体启动入口。业务模块均位于 com.aiplatform 下。
 */
@SpringBootApplication(scanBasePackages = "com.aiplatform")
@ConfigurationPropertiesScan(basePackages = "com.aiplatform")
@MapperScan(basePackages = "com.aiplatform", annotationClass = Mapper.class)
public class AiPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPlatformApplication.class, args);
    }
}
