package com.aiplatform.agent.service.impl;

import com.aiplatform.agent.dto.AgentDebugRequest;
import com.aiplatform.agent.dto.AgentExecutionQuery;
import com.aiplatform.agent.entity.Agent;
import com.aiplatform.agent.entity.AgentExecution;
import com.aiplatform.agent.entity.AgentStep;
import com.aiplatform.agent.enums.AgentExecutionStatus;
import com.aiplatform.agent.exception.AgentLimitExceededException;
import com.aiplatform.agent.mapper.AgentExecutionMapper;
import com.aiplatform.agent.mapper.AgentMapper;
import com.aiplatform.agent.mapper.AgentStepMapper;
import com.aiplatform.agent.service.AgentAssistant;
import com.aiplatform.agent.service.AgentRunRecorder;
import com.aiplatform.agent.service.AgentRuntimeService;
import com.aiplatform.agent.service.ToolSchemaConverter;
import com.aiplatform.agent.vo.AgentExecutionDetailVO;
import com.aiplatform.agent.vo.AgentExecutionVO;
import com.aiplatform.agent.vo.AgentStepVO;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.PageResult;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.model.adapter.ModelAdapterRegistry;
import com.aiplatform.model.entity.ModelConfig;
import com.aiplatform.model.enums.ModelCapability;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.runtime.ModelRuntime;
import com.aiplatform.model.service.ModelCallLogService;
import com.aiplatform.model.service.ModelRuntimeFactory;
import com.aiplatform.tool.executor.ToolDefinition;
import com.aiplatform.tool.executor.ToolExecutionResult;
import com.aiplatform.tool.service.ToolExecutionService;
import com.aiplatform.tool.service.ToolService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行引擎：使用 LangChain4j AI Services 完成模型 + Tool 的自主编排，
 * 并在每次模型/工具调用时记录步骤、Token、耗时和限额。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeServiceImpl implements AgentRuntimeService {

    private static final int MAX_STEP_CONTENT = 8000;

    private final AgentMapper agentMapper;
    private final AgentExecutionMapper agentExecutionMapper;
    private final AgentStepMapper agentStepMapper;
    private final ToolService toolService;
    private final ToolExecutionService toolExecutionService;
    private final ModelRuntimeFactory modelRuntimeFactory;
    private final ModelAdapterRegistry modelAdapterRegistry;
    private final ToolSchemaConverter toolSchemaConverter;
    private final ModelCallLogService modelCallLogService;
    private final ObjectMapper objectMapper;

    @Override
    public AgentExecutionDetailVO debug(Long agentId, AgentDebugRequest request) {
        Agent agent = requireAgent(agentId);
        if (!Boolean.TRUE.equals(agent.getEnabled())) {
            throw BusinessException.badRequest("Agent 已停用，无法调试");
        }
        Long userId = SecurityUtils.currentUserId();
        ModelRuntime runtime = modelRuntimeFactory.resolveForExecution(agent.getModelId());
        if (runtime.modelType() != ModelType.TEXT && runtime.modelType() != ModelType.VISION) {
            throw BusinessException.badRequest("Agent 只能使用文本或视觉模型");
        }

        List<Long> toolIds = parseToolIds(agent.getToolIds());
        List<ToolDefinition> tools = toolService.findEnabledDefinitions(toolIds);
        if (!tools.isEmpty() && !runtime.supports(ModelCapability.TOOL_CALLING)) {
            throw BusinessException.badRequest("当前模型未声明 TOOL_CALLING 能力，无法执行绑定工具");
        }

        AgentExecution execution = new AgentExecution();
        execution.setAgentId(agent.getId());
        execution.setAgentName(agent.getName());
        execution.setAgentVersion(agent.getCurrentVersion());
        execution.setModelName(runtime.displayName());
        execution.setUserId(userId);
        execution.setInputText(request.input());
        execution.setStatus(AgentExecutionStatus.RUNNING.name());
        execution.setToolCallCount(0);
        execution.setModelCallCount(0);
        execution.setTokenEstimated(false);
        execution.setTraceId(MDC.get("traceId"));
        execution.setCreatedAt(LocalDateTime.now());
        agentExecutionMapper.insert(execution);

        AgentRunRecorder recorder = new AgentRunRecorder(agent, execution, runtime,
                agentStepMapper, modelCallLogService);
        long timeout = Math.max(10, Math.min(agent.getMaxExecutionSeconds() == null
                ? 90 : agent.getMaxExecutionSeconds(), 300));
        ModelRuntime executionRuntime = runtime.withTimeoutSeconds(timeout);

        String status = AgentExecutionStatus.SUCCESS.name();
        String output = null;
        String errorMessage = null;

        try {
            ChatModelListener listener = new ChatModelListener() {
                @Override
                public void onRequest(dev.langchain4j.model.chat.listener.ChatModelRequestContext context) {
                    recorder.beginModelRequest(context.chatRequest());
                }

                @Override
                public void onResponse(dev.langchain4j.model.chat.listener.ChatModelResponseContext context) {
                    recorder.completeModelResponse(context.chatResponse());
                }

                @Override
                public void onError(dev.langchain4j.model.chat.listener.ChatModelErrorContext context) {
                    recorder.onModelError(context.error(), context.chatRequest());
                }
            };

            ChatModel chatModel = modelAdapterRegistry.chatModel(executionRuntime, List.of(listener));
            Map<ToolSpecification, ToolExecutor> toolExecutors = buildToolExecutors(
                    tools, recorder, execution, userId);
            AiServices<AgentAssistant> builder = AiServices.builder(AgentAssistant.class)
                    .chatModel(chatModel)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                    .systemMessageProvider(memoryId -> agent.getSystemPrompt());
            if (!toolExecutors.isEmpty()) {
                builder.tools(toolExecutors)
                        .maxSequentialToolsInvocations(Math.max(1, agent.getMaxToolCalls() + 1));
            }
            AgentAssistant assistant = builder.build();
            output = assistant.chat(request.input());
            recorder.finalCheck();
            if (recorder.isLimitExceeded()) {
                status = AgentExecutionStatus.LIMIT_EXCEEDED.name();
                errorMessage = recorder.getLimitMessage();
                recorder.recordError(errorMessage);
            }
        } catch (Exception ex) {
            Throwable limit = findLimit(ex);
            if (limit != null || recorder.isLimitExceeded()) {
                status = AgentExecutionStatus.LIMIT_EXCEEDED.name();
                errorMessage = limit == null ? recorder.getLimitMessage() : limit.getMessage();
                recorder.markLimit(errorMessage);
                recorder.recordError(errorMessage);
            } else {
                status = AgentExecutionStatus.FAILED.name();
                errorMessage = safeError(ex);
                recorder.recordError(errorMessage);
                log.warn("Agent {} execution failed: {}", agentId, errorMessage);
            }
        }

        updateExecution(execution.getId(), output, status, errorMessage, recorder);
        return loadDetail(execution.getId());
    }

    @Override
    public PageResult<AgentExecutionVO> executions(AgentExecutionQuery query) {
        LambdaQueryWrapper<AgentExecution> wrapper = Wrappers.<AgentExecution>lambdaQuery()
                .orderByDesc(AgentExecution::getId);
        if (query.getAgentId() != null) {
            wrapper.eq(AgentExecution::getAgentId, query.getAgentId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            try {
                wrapper.eq(AgentExecution::getStatus,
                        AgentExecutionStatus.valueOf(query.getStatus().trim().toUpperCase()).name());
            } catch (Exception ex) {
                throw BusinessException.badRequest("执行状态不合法");
            }
        }
        Page<AgentExecution> page = agentExecutionMapper.selectPage(
                new Page<>(query.currentPage(), query.pageSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(AgentExecutionVO::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public AgentExecutionDetailVO executionDetail(Long executionId) {
        AgentExecution execution = agentExecutionMapper.selectById(executionId);
        if (execution == null) {
            throw BusinessException.notFound("Agent 执行记录不存在");
        }
        List<AgentStepVO> steps = loadSteps(executionId);
        return new AgentExecutionDetailVO(AgentExecutionVO.from(execution), steps);
    }

    private Map<ToolSpecification, ToolExecutor> buildToolExecutors(List<ToolDefinition> tools,
                                                                    AgentRunRecorder recorder,
                                                                    AgentExecution execution,
                                                                    Long userId) {
        Map<ToolSpecification, ToolExecutor> executors = new LinkedHashMap<>();
        for (ToolDefinition tool : tools) {
            ToolSpecification specification = toolSchemaConverter.toSpecification(tool);
            ToolExecutor executor = (request, memoryId) -> executeTool(tool, request, recorder, execution, userId);
            executors.put(specification, executor);
        }
        return executors;
    }

    private String executeTool(ToolDefinition definition, ToolExecutionRequest request,
                               AgentRunRecorder recorder, AgentExecution execution, Long userId) {
        String argumentsJson = StringUtils.hasText(request.arguments()) ? request.arguments() : "{}";
        Map<String, Object> arguments;
        try {
            arguments = objectMapper.readValue(argumentsJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            recorder.recordError("工具参数 JSON 无法解析：" + safeError(ex));
            return toolError("工具参数不是合法 JSON");
        }

        try {
            recorder.beforeTool(definition, argumentsJson);
            ToolExecutionResult result = toolExecutionService.executeById(
                    definition.id(), arguments, userId, execution.getId(), true);
            recorder.afterTool(definition, argumentsJson, result);
            if (result.success()) {
                return result.resultText();
            }
            return toolError(result.errorMessage());
        } catch (AgentLimitExceededException ex) {
            throw ex;
        } catch (Exception ex) {
            String message = safeError(ex);
            recorder.recordError("工具执行异常：" + message);
            return toolError(message);
        }
    }

    private AgentExecutionDetailVO loadDetail(Long executionId) {
        AgentExecution execution = agentExecutionMapper.selectById(executionId);
        if (execution == null) {
            throw BusinessException.notFound("Agent 执行记录不存在");
        }
        return new AgentExecutionDetailVO(AgentExecutionVO.from(execution), loadSteps(executionId));
    }

    private List<AgentStepVO> loadSteps(Long executionId) {
        return agentStepMapper.selectList(Wrappers.<AgentStep>lambdaQuery()
                        .eq(AgentStep::getExecutionId, executionId)
                        .orderByAsc(AgentStep::getStepIndex))
                .stream()
                .map(AgentStepVO::from)
                .toList();
    }

    private void updateExecution(Long executionId, String output, String status,
                                 String errorMessage, AgentRunRecorder recorder) {
        agentExecutionMapper.update(null, new LambdaUpdateWrapper<AgentExecution>()
                .eq(AgentExecution::getId, executionId)
                .set(AgentExecution::getOutputText, output)
                .set(AgentExecution::getStatus, status)
                .set(AgentExecution::getToolCallCount, recorder.getToolCallCount())
                .set(AgentExecution::getModelCallCount, recorder.getModelCallCount())
                .set(AgentExecution::getPromptTokens, recorder.getPromptTokens())
                .set(AgentExecution::getCompletionTokens, recorder.getCompletionTokens())
                .set(AgentExecution::getTotalTokens, recorder.getTotalTokens())
                .set(AgentExecution::getTokenEstimated, recorder.isEstimated())
                .set(AgentExecution::getEstimatedCost, recorder.getEstimatedCost())
                .set(AgentExecution::getLatencyMs, recorder.getLatencyMillis())
                .set(AgentExecution::getErrorMessage, limit(errorMessage, 2000))
                .set(AgentExecution::getFinishedAt, LocalDateTime.now()));
    }

    private Agent requireAgent(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw BusinessException.notFound("Agent 不存在");
        }
        return agent;
    }

    private List<Long> parseToolIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Long> values = objectMapper.readValue(json, new TypeReference<>() {
            });
            return values == null ? List.of() : values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String toolError(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("error", message == null ? "工具执行失败" : message);
        try {
            return objectMapper.writeValueAsString(error);
        } catch (Exception ex) {
            return "{\"success\":false,\"error\":\"工具执行失败\"}";
        }
    }

    private Throwable findLimit(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof AgentLimitExceededException) {
                return current;
            }
            current = current.getCause();
            depth++;
        }
        return null;
    }

    private String safeError(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        if (!StringUtils.hasText(message)) {
            message = error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        return limit(message.replaceAll("[\\r\\n\\t]+", " ").trim(), 1000);
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
