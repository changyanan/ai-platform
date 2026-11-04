package com.aiplatform.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ChatExecutorConfig {

    /**
     * SSE 模型调用使用虚拟线程，避免长连接占用 Tomcat 工作线程。
     */
    @Bean(name = "chatExecutor", destroyMethod = "shutdown")
    public ExecutorService chatExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
