package com.aiplatform.tool.service;

import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.tool.entity.AiTool;
import com.aiplatform.tool.entity.ToolExecutionLog;
import com.aiplatform.tool.executor.PlatformTool;
import com.aiplatform.tool.executor.ToolArgumentValidator;
import com.aiplatform.tool.executor.ToolBeanRegistry;
import com.aiplatform.tool.executor.ToolExecutionResult;
import com.aiplatform.tool.mapper.AiToolMapper;
import com.aiplatform.tool.mapper.ToolExecutionLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具真实执行入口：参数校验、Java Bean 调用、耗时与日志落库。
 * 任何失败都会返回 success=false 并保留明确错误，不产生“声称成功”的结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutionService {

    private static final int MAX_LOG_TEXT = 8000;

    private final AiToolMapper aiToolMapper;
    private final ToolExecutionLogMapper toolExecutionLogMapper;
    private final ToolBeanRegistry toolBeanRegistry;
    private final ToolArgumentValidator argumentValidator;
    private final ObjectMapper objectMapper;

    public ToolExecutionResult executeById(Long toolId, Map<String, Object> rawArguments,
                                           Long userId, Long agentExecutionId, boolean requireEnabled) {
        AiTool tool = aiToolMapper.selectById(toolId);
        if (tool == null) {
            throw BusinessException.notFound("工具不存在");
        }
        long start = System.currentTimeMillis();
        String traceId = MDC.get("traceId");
        String argumentsJson = serializeArguments(rawArguments);
        LocalDateTime createdAt = LocalDateTime.now();

        if (requireEnabled && !Boolean.TRUE.equals(tool.getEnabled())) {
            long duration = System.currentTimeMillis() - start;
            String error = "工具「" + tool.getName() + "」已停用";
            Long logId = recordLog(tool, userId, agentExecutionId, argumentsJson, null,
                    false, error, duration, traceId, createdAt);
            return new ToolExecutionResult(logId, tool.getId(), tool.getToolCode(), tool.getName(),
                    false, null, error, duration, createdAt);
        }

        try {
            Map<String, Object> arguments = argumentValidator.validateAndNormalize(
                    tool.getParametersSchema(), rawArguments);
            PlatformTool platformTool = toolBeanRegistry.require(tool.getBeanName());
            String result = platformTool.execute(arguments);
            if (result == null) {
                result = "";
            }
            long duration = System.currentTimeMillis() - start;
            Long logId = recordLog(tool, userId, agentExecutionId, serializeArguments(arguments), result,
                    true, null, duration, traceId, LocalDateTime.now());
            log.info("Tool {} executed by user {} in {}ms", tool.getToolCode(), userId, duration);
            return new ToolExecutionResult(logId, tool.getId(), tool.getToolCode(), tool.getName(),
                    true, result, null, duration, createdAt);
        } catch (BusinessException ex) {
            long duration = System.currentTimeMillis() - start;
            Long logId = recordLog(tool, userId, agentExecutionId, argumentsJson, null,
                    false, ex.getMessage(), duration, traceId, LocalDateTime.now());
            log.info("Tool {} business failure: {}", tool.getToolCode(), ex.getMessage());
            return new ToolExecutionResult(logId, tool.getId(), tool.getToolCode(), tool.getName(),
                    false, null, ex.getMessage(), duration, createdAt);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - start;
            log.error("Tool {} execution failed", tool.getToolCode(), ex);
            String error = "工具执行异常：" + safeError(ex);
            Long logId = recordLog(tool, userId, agentExecutionId, argumentsJson, null,
                    false, error, duration, traceId, LocalDateTime.now());
            return new ToolExecutionResult(logId, tool.getId(), tool.getToolCode(), tool.getName(),
                    false, null, error, duration, createdAt);
        }
    }

    private Long recordLog(AiTool tool, Long userId, Long agentExecutionId, String argumentsJson,
                           String resultText, boolean success, String error, long durationMs,
                           String traceId, LocalDateTime createdAt) {
        try {
            ToolExecutionLog logEntity = new ToolExecutionLog();
            logEntity.setToolId(tool.getId());
            logEntity.setToolCode(tool.getToolCode());
            logEntity.setUserId(userId);
            logEntity.setAgentExecutionId(agentExecutionId);
            logEntity.setArgumentsJson(limit(argumentsJson, MAX_LOG_TEXT));
            logEntity.setResultText(limit(resultText, MAX_LOG_TEXT));
            logEntity.setSuccess(success);
            logEntity.setErrorMessage(limit(error, 1000));
            logEntity.setDurationMs(durationMs);
            logEntity.setTraceId(traceId);
            logEntity.setCreatedAt(createdAt);
            toolExecutionLogMapper.insert(logEntity);
            return logEntity.getId();
        } catch (Exception ex) {
            log.warn("Failed to persist tool execution log: {}", ex.getMessage());
            return null;
        }
    }

    private String serializeArguments(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? new LinkedHashMap<>() : arguments;
        try {
            return limit(objectMapper.writeValueAsString(safeArguments), MAX_LOG_TEXT);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String safeError(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        if (!StringUtils.hasText(message)) {
            message = error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        return limit(message.replaceAll("[\\r\\n\\t]+", " ").trim(), 500);
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
