package com.aiplatform.rag.service;

import com.aiplatform.common.ai.TokenEstimator;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.model.adapter.ModelAdapterRegistry;
import com.aiplatform.model.enums.ModelCallType;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.runtime.ModelRuntime;
import com.aiplatform.model.service.ModelCallLogCommand;
import com.aiplatform.model.service.ModelCallLogService;
import com.aiplatform.model.service.ModelRuntimeFactory;
import com.aiplatform.rag.dto.RagQueryRequest;
import com.aiplatform.rag.entity.RagQueryLog;
import com.aiplatform.rag.mapper.RagQueryLogMapper;
import com.aiplatform.rag.vo.RagQueryVO;
import com.aiplatform.rag.vo.RagSourceVO;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryService {

    private final RagRetrievalService retrievalService;
    private final ModelRuntimeFactory modelRuntimeFactory;
    private final ModelAdapterRegistry modelAdapterRegistry;
    private final ModelCallLogService modelCallLogService;
    private final RagQueryLogMapper ragQueryLogMapper;

    public RagQueryVO query(RagQueryRequest request) {
        long start = System.currentTimeMillis();
        Long userId = SecurityUtils.currentUserId();
        ModelRuntime runtime = modelRuntimeFactory.resolveForExecution(request.modelId());
        if (runtime.modelType() != ModelType.TEXT && runtime.modelType() != ModelType.VISION) {
            throw BusinessException.badRequest("RAG 问答需要文本或视觉模型");
        }
        int topK = request.topK() == null ? 5 : request.topK();
        List<RagSourceVO> sources = retrievalService.searchSelected(
                request.knowledgeBaseIds(), request.question(), topK);

        if (sources.isEmpty()) {
            String answer = "未在所选知识库中检索到相关内容。请确认文档已成功完成向量化，或换一种问法。";
            long latency = System.currentTimeMillis() - start;
            recordQueryLog(request, userId, runtime, answer, sources.size(), null, null, null,
                    false, latency, true, null);
            return new RagQueryVO(request.question(), answer, runtime.displayName(), List.of(),
                    null, null, null, false, latency, false);
        }

        String prompt = buildPrompt(request.question(), sources);
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;
        boolean estimated = false;
        String answer;
        try {
            ChatModel chatModel = modelAdapterRegistry.chatModel(runtime);
            ChatResponse response = chatModel.chat(UserMessage.from(prompt));
            answer = response.aiMessage() == null || response.aiMessage().text() == null
                    ? "" : response.aiMessage().text().trim();
            TokenUsage usage = response.tokenUsage();
            if (usage != null && usage.inputTokenCount() != null && usage.outputTokenCount() != null) {
                promptTokens = usage.inputTokenCount();
                completionTokens = usage.outputTokenCount();
                totalTokens = usage.totalTokenCount() != null
                        ? usage.totalTokenCount() : promptTokens + completionTokens;
            } else {
                promptTokens = TokenEstimator.estimate(prompt);
                completionTokens = TokenEstimator.estimate(answer);
                totalTokens = promptTokens + completionTokens;
                estimated = true;
            }
        } catch (Exception ex) {
            String message = safeError(ex);
            long latency = System.currentTimeMillis() - start;
            recordQueryLog(request, userId, runtime, null, sources.size(), null, null, null,
                    true, latency, false, message);
            recordModelCall(runtime, userId, false, null, null, null, latency, true, message, sources.size());
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "RAG 回答生成失败：" + message);
        }

        long latency = System.currentTimeMillis() - start;
        recordModelCall(runtime, userId, true, promptTokens, completionTokens, totalTokens,
                latency, estimated, null, sources.size());
        recordQueryLog(request, userId, runtime, answer, sources.size(), promptTokens,
                completionTokens, totalTokens, estimated, latency, true, null);
        return new RagQueryVO(request.question(), answer, runtime.displayName(), sources,
                promptTokens, completionTokens, totalTokens, estimated, latency, true);
    }

    private String buildPrompt(String question, List<RagSourceVO> sources) {
        StringBuilder context = new StringBuilder();
        int index = 1;
        for (RagSourceVO source : sources) {
            String block = "[" + index + "] 知识库：" + source.knowledgeBaseName()
                    + " / 文档：" + source.documentName()
                    + " / 片段：" + (source.chunkIndex() + 1) + "\n"
                    + source.content() + "\n\n";
            if (context.length() + block.length() > 9000) {
                break;
            }
            context.append(block);
            index++;
        }
        return """
                你是企业知识库问答助手。请严格依据下面的资料回答问题。
                要求：
                1. 只使用资料中的信息，不得补充资料未提及的业务事实；
                2. 关键结论后使用 [编号] 标注来源；
                3. 如果资料不足以回答，明确说明“知识库未提供相关信息”，不要猜测；
                4. 使用简体中文，结构清晰。

                资料：
                %s
                问题：%s
                """.formatted(context, question);
    }

    private void recordModelCall(ModelRuntime runtime, Long userId, boolean success,
                                 Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                 long latency, boolean estimated, String error, int sourceCount) {
        modelCallLogService.record(ModelCallLogCommand.builder()
                .traceId(MDC.get("traceId"))
                .userId(userId)
                .modelConfigId(runtime.configId())
                .providerCode(runtime.providerCode())
                .modelName(runtime.modelName())
                .requestType(ModelCallType.RAG_ANSWER.name())
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .latencyMs(latency)
                .success(success)
                .errorCode(success ? null : "RAG_ANSWER_FAILED")
                .errorMessage(error)
                .estimated(estimated)
                .inputPricePerThousand(runtime.inputPricePerThousand())
                .outputPricePerThousand(runtime.outputPricePerThousand())
                .currency(runtime.currency())
                .requestSummary("rag answer · " + sourceCount + " sources")
                .build());
    }

    private void recordQueryLog(RagQueryRequest request, Long userId, ModelRuntime runtime,
                                String answer, int sourceCount, Integer promptTokens,
                                Integer completionTokens, Integer totalTokens, boolean estimated,
                                long latency, boolean success, String error) {
        try {
            RagQueryLog entity = new RagQueryLog();
            entity.setUserId(userId);
            entity.setKnowledgeBaseIds(request.knowledgeBaseIds().stream()
                    .distinct().map(String::valueOf).collect(Collectors.joining(",")));
            entity.setQuestion(limit(request.question(), 5000));
            entity.setAnswer(answer);
            entity.setModelConfigId(runtime.configId());
            entity.setModelName(runtime.displayName());
            entity.setTopK(request.topK() == null ? 5 : request.topK());
            entity.setSourceCount(sourceCount);
            entity.setPromptTokens(promptTokens);
            entity.setCompletionTokens(completionTokens);
            entity.setTotalTokens(totalTokens);
            entity.setTokenEstimated(estimated);
            entity.setLatencyMs(latency);
            entity.setSuccess(success);
            entity.setErrorMessage(limit(error, 2000));
            entity.setTraceId(MDC.get("traceId"));
            entity.setCreatedAt(LocalDateTime.now());
            ragQueryLogMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("Failed to persist RAG query log: {}", ex.getMessage());
        }
    }

    private String safeError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
