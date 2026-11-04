package com.aiplatform.agent.service;

import com.aiplatform.agent.entity.Agent;
import com.aiplatform.agent.entity.AgentExecution;
import com.aiplatform.agent.entity.AgentStep;
import com.aiplatform.agent.enums.AgentStepType;
import com.aiplatform.agent.exception.AgentLimitExceededException;
import com.aiplatform.agent.mapper.AgentStepMapper;
import com.aiplatform.common.ai.TokenEstimator;
import com.aiplatform.model.enums.ModelCallType;
import com.aiplatform.model.runtime.ModelRuntime;
import com.aiplatform.model.service.ModelCallLogCommand;
import com.aiplatform.model.service.ModelCallLogService;
import com.aiplatform.tool.executor.ToolDefinition;
import com.aiplatform.tool.executor.ToolExecutionResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次 Agent 执行的状态记录与限额守卫。
 * 记录模型请求/响应、工具调用/结果，并把 Token、耗时、成本写回执行记录。
 */
@Slf4j
public class AgentRunRecorder {

    private static final int HARD_MAX_MODEL_CALLS = 8;
    private static final int MAX_STEP_TEXT = 8000;

    private final Agent agent;
    private final AgentExecution execution;
    private final ModelRuntime runtime;
    private final AgentStepMapper stepMapper;
    private final ModelCallLogService modelCallLogService;
    private final long startedAtMillis = System.currentTimeMillis();
    private final long deadlineMillis;
    private final AtomicInteger stepIndex = new AtomicInteger(1);
    private final AtomicInteger modelCalls = new AtomicInteger(0);
    private final AtomicInteger toolCalls = new AtomicInteger(0);

    private int promptTokens;
    private int completionTokens;
    private boolean estimated;
    private boolean costKnown;
    private BigDecimal accumulatedCost = BigDecimal.ZERO;
    private boolean limitExceeded;
    private String limitMessage;
    private long currentModelStartMillis;
    private String currentRequestText = "";

    public AgentRunRecorder(Agent agent, AgentExecution execution, ModelRuntime runtime,
                            AgentStepMapper stepMapper, ModelCallLogService modelCallLogService) {
        this.agent = agent;
        this.execution = execution;
        this.runtime = runtime;
        this.stepMapper = stepMapper;
        this.modelCallLogService = modelCallLogService;
        int seconds = agent.getMaxExecutionSeconds() == null ? 90 : agent.getMaxExecutionSeconds();
        this.deadlineMillis = startedAtMillis + seconds * 1000L;
        this.costKnown = runtime.inputPricePerThousand() != null
                && runtime.outputPricePerThousand() != null;
    }

    public synchronized void beginModelRequest(ChatRequest request) {
        ensureWithinLimits();
        int number = modelCalls.incrementAndGet();
        if (number > HARD_MAX_MODEL_CALLS) {
            markLimit("超过单次执行最大模型调用次数 " + HARD_MAX_MODEL_CALLS);
            throw new AgentLimitExceededException(limitMessage);
        }
        currentModelStartMillis = System.currentTimeMillis();
        currentRequestText = request == null ? "" : limit(request.messages() == null
                ? "" : request.messages().toString(), 12000);
        String lastMessage = lastMessagePreview(request);
        recordStep(AgentStepType.MODEL_REQUEST, "模型请求 #" + number,
                lastMessage, null, null, null, null, null, true);
    }

    public synchronized void completeModelResponse(ChatResponse response) {
        long duration = currentModelStartMillis == 0
                ? 0 : System.currentTimeMillis() - currentModelStartMillis;
        currentModelStartMillis = 0;
        String content = "";
        String toolSummary = "";
        TokenUsage usage = response == null ? null : response.tokenUsage();
        if (response != null && response.aiMessage() != null) {
            AiMessage aiMessage = response.aiMessage();
            if (aiMessage.text() != null) {
                content = aiMessage.text();
            }
            if (aiMessage.hasToolExecutionRequests()) {
                toolSummary = aiMessage.toolExecutionRequests().stream()
                        .map(request -> request.name())
                        .distinct()
                        .toList()
                        .toString();
                content = (content.isBlank() ? "" : content + "\n")
                        + "请求工具：" + toolSummary;
            }
        }

        Integer prompt;
        Integer completion;
        boolean callEstimated = false;
        if (usage != null && usage.inputTokenCount() != null && usage.outputTokenCount() != null) {
            prompt = usage.inputTokenCount();
            completion = usage.outputTokenCount();
        } else {
            prompt = TokenEstimator.estimate(currentRequestText);
            completion = TokenEstimator.estimate(content);
            callEstimated = true;
            estimated = true;
        }
        promptTokens += prompt;
        completionTokens += completion;

        recordStep(AgentStepType.MODEL_RESPONSE, "模型响应 #" + modelCalls.get(),
                limit(content, MAX_STEP_TEXT), null, null, null, prompt, completion, duration, true);
        recordCallLog(true, duration, prompt, completion, callEstimated, null);

        BigDecimal callCost = calculateCost(prompt, completion);
        if (callCost != null) {
            accumulatedCost = accumulatedCost.add(callCost);
        }
        if (agent.getMaxCost() != null && costKnown && accumulatedCost.compareTo(agent.getMaxCost()) > 0) {
            markLimit("估算成本 " + accumulatedCost.stripTrailingZeros().toPlainString()
                    + " 已超过上限 " + agent.getMaxCost().stripTrailingZeros().toPlainString());
        }
        if (System.currentTimeMillis() > deadlineMillis) {
            markLimit("超过最大执行时间 " + agent.getMaxExecutionSeconds() + " 秒");
        }
    }

    public synchronized void onModelError(Throwable error, ChatRequest request) {
        long duration = currentModelStartMillis == 0
                ? 0 : System.currentTimeMillis() - currentModelStartMillis;
        currentModelStartMillis = 0;
        String message = error == null ? "模型调用失败" : safeError(error);
        recordStep(AgentStepType.ERROR, "模型调用失败", message, null, null, null, null, null, duration, false);
        int prompt = TokenEstimator.estimate(request == null || request.messages() == null
                ? "" : request.messages().toString());
        recordCallLog(false, duration, prompt, 0, true, message);
    }

    public synchronized void beforeTool(ToolDefinition definition, String argumentsJson) {
        ensureWithinLimits();
        int max = agent.getMaxToolCalls() == null ? 5 : agent.getMaxToolCalls();
        int nextCount = toolCalls.get() + 1;
        if (nextCount > max) {
            markLimit("超过最大工具调用次数 " + max);
            throw new AgentLimitExceededException(limitMessage);
        }
        toolCalls.incrementAndGet();
        recordStep(AgentStepType.TOOL_REQUEST, "调用工具 · " + definition.name(),
                definition.description(), definition.toolCode(), argumentsJson, null, null, null, true);
    }

    public synchronized void afterTool(ToolDefinition definition, String argumentsJson,
                                       ToolExecutionResult result) {
        recordStep(AgentStepType.TOOL_RESULT,
                result.success() ? "工具返回 · " + definition.name() : "工具失败 · " + definition.name(),
                result.success() ? result.resultText() : result.errorMessage(),
                definition.toolCode(), argumentsJson,
                result.success() ? limit(result.resultText(), MAX_STEP_TEXT) : null,
                null, null, result.durationMs(), result.success());
        if (System.currentTimeMillis() > deadlineMillis) {
            markLimit("超过最大执行时间 " + agent.getMaxExecutionSeconds() + " 秒");
        }
    }

    public synchronized void finalCheck() {
        if (limitExceeded) {
            return;
        }
        long elapsed = System.currentTimeMillis() - startedAtMillis;
        if (elapsed > (agent.getMaxExecutionSeconds() == null ? 90 : agent.getMaxExecutionSeconds()) * 1000L) {
            markLimit("超过最大执行时间 " + agent.getMaxExecutionSeconds() + " 秒");
        }
        if (agent.getMaxCost() != null && costKnown && accumulatedCost.compareTo(agent.getMaxCost()) > 0) {
            markLimit("估算成本已超过上限");
        }
    }

    public synchronized void recordError(String message) {
        recordStep(AgentStepType.ERROR, "执行终止", safeText(message), null, null, null,
                null, null, null, false);
    }

    public synchronized boolean isLimitExceeded() {
        return limitExceeded;
    }

    public synchronized String getLimitMessage() {
        return limitMessage;
    }

    public synchronized int getPromptTokens() {
        return promptTokens;
    }

    public synchronized int getCompletionTokens() {
        return completionTokens;
    }

    public synchronized int getTotalTokens() {
        return promptTokens + completionTokens;
    }

    public synchronized boolean isEstimated() {
        return estimated;
    }

    public synchronized int getModelCallCount() {
        return modelCalls.get();
    }

    public synchronized int getToolCallCount() {
        return toolCalls.get();
    }

    public synchronized BigDecimal getEstimatedCost() {
        return costKnown ? accumulatedCost.setScale(8, RoundingMode.HALF_UP) : null;
    }

    public long getLatencyMillis() {
        return System.currentTimeMillis() - startedAtMillis;
    }

    public void markLimit(String message) {
        if (!limitExceeded) {
            limitExceeded = true;
            limitMessage = message;
        }
    }

    private void ensureWithinLimits() {
        if (limitExceeded) {
            throw new AgentLimitExceededException(limitMessage);
        }
        if (System.currentTimeMillis() > deadlineMillis) {
            markLimit("超过最大执行时间 " + agent.getMaxExecutionSeconds() + " 秒");
            throw new AgentLimitExceededException(limitMessage);
        }
        if (agent.getMaxCost() != null && costKnown && accumulatedCost.compareTo(agent.getMaxCost()) > 0) {
            markLimit("估算成本已超过上限 " + agent.getMaxCost().stripTrailingZeros().toPlainString());
            throw new AgentLimitExceededException(limitMessage);
        }
    }

    private void recordCallLog(boolean success, long latency, Integer prompt, Integer completion,
                               boolean callEstimated, String errorMessage) {
        Integer total = prompt == null || completion == null ? null : prompt + completion;
        modelCallLogService.record(ModelCallLogCommand.builder()
                .traceId(execution.getTraceId())
                .userId(execution.getUserId())
                .modelConfigId(runtime.configId())
                .providerCode(runtime.providerCode())
                .modelName(runtime.modelName())
                .requestType(ModelCallType.AGENT.name())
                .promptTokens(prompt)
                .completionTokens(completion)
                .totalTokens(total)
                .latencyMs(latency)
                .success(success)
                .errorCode(success ? null : "AGENT_MODEL_CALL_FAILED")
                .errorMessage(errorMessage)
                .estimated(callEstimated)
                .inputPricePerThousand(runtime.inputPricePerThousand())
                .outputPricePerThousand(runtime.outputPricePerThousand())
                .currency(runtime.currency())
                .requestSummary("agent execution #" + execution.getId() + " · " + agent.getName())
                .build());
    }

    private BigDecimal calculateCost(Integer prompt, Integer completion) {
        if (!costKnown || prompt == null || completion == null) {
            return null;
        }
        BigDecimal promptCost = BigDecimal.valueOf(prompt)
                .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                .multiply(runtime.inputPricePerThousand());
        BigDecimal completionCost = BigDecimal.valueOf(completion)
                .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                .multiply(runtime.outputPricePerThousand());
        return promptCost.add(completionCost);
    }

    private void recordStep(AgentStepType type, String title, String content,
                            String toolCode, String argumentsJson, String toolResult,
                            Integer prompt, Integer completion, Long duration, boolean success) {
        try {
            AgentStep step = new AgentStep();
            step.setExecutionId(execution.getId());
            step.setStepIndex(stepIndex.getAndIncrement());
            step.setStepType(type.name());
            step.setTitle(limit(title, 200));
            step.setContent(limit(content, MAX_STEP_TEXT));
            step.setToolCode(toolCode);
            step.setArgumentsJson(limit(argumentsJson, MAX_STEP_TEXT));
            step.setToolResult(limit(toolResult, MAX_STEP_TEXT));
            step.setPromptTokens(prompt);
            step.setCompletionTokens(completion);
            step.setDurationMs(duration);
            step.setSuccess(success);
            step.setCreatedAt(LocalDateTime.now());
            stepMapper.insert(step);
        } catch (Exception ex) {
            log.warn("Failed to persist agent step: {}", ex.getMessage());
        }
    }

    private void recordStep(AgentStepType type, String title, String content,
                            String toolCode, String argumentsJson, String toolResult,
                            Integer prompt, Integer completion, boolean success) {
        recordStep(type, title, content, toolCode, argumentsJson, toolResult, prompt, completion, null, success);
    }

    private String lastMessagePreview(ChatRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) {
            return null;
        }
        String value = request.messages().get(request.messages().size() - 1).toString();
        return limit(value, 2000);
    }

    private String safeError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        return limit(message.replaceAll("[\\r\\n\\t]+", " ").trim(), 1000);
    }

    private String safeText(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
