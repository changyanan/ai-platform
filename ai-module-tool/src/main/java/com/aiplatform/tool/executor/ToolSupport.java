package com.aiplatform.tool.executor;

import com.aiplatform.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class ToolSupport {

    private ToolSupport() {
    }

    public static String requireString(Map<String, Object> arguments, String key, String label) {
        Object value = arguments.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw BusinessException.badRequest("缺少参数：" + label);
        }
        return text.trim();
    }

    public static String optionalString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    public static boolean optionalBoolean(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value instanceof Boolean bool && bool;
    }

    public static String toJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("工具结果序列化失败", ex);
        }
    }
}
