package com.aiplatform.agent.exception;

public class AgentLimitExceededException extends RuntimeException {

    public AgentLimitExceededException(String message) {
        super(message);
    }
}
