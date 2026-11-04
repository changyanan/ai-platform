package com.aiplatform.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai-platform.chat")
public class ChatProperties {

    private int maxContextMessages = 20;
    private long sseTimeoutMs = 300_000;
    private int maxInputChars = 20_000;
    private String systemPrompt = """
            你是企业级 AI 智能应用平台中的智能助手。
            请使用简体中文回答，保持专业、准确、结构清晰。
            如果信息不足，请明确说明不确定性；不要声称执行了并未执行的工具或系统操作。
            """;
}
