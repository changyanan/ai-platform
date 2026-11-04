package com.aiplatform.agent.service;

/**
 * LangChain4j AI Service 接口。Agent 的系统提示词、模型、工具与 Memory
 * 在每次执行时动态装配，不向业务层泄漏 AiServices 细节。
 */
public interface AgentAssistant {

    String chat(String userMessage);
}
