package com.aiplatform.rag.embedding;

import com.aiplatform.common.ai.TokenEstimator;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.model.adapter.ModelAdapterRegistry;
import com.aiplatform.model.enums.ModelCallType;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.runtime.ModelRuntime;
import com.aiplatform.model.service.ModelCallLogCommand;
import com.aiplatform.model.service.ModelCallLogService;
import com.aiplatform.model.service.ModelRuntimeFactory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 真实 Embedding 调用，按批次执行并逐批写入模型调用日志。
 * 不生成随机向量、不缓存伪造结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEmbeddingService {

    private static final int BATCH_SIZE = 32;

    private final ModelRuntimeFactory modelRuntimeFactory;
    private final ModelAdapterRegistry modelAdapterRegistry;
    private final ModelCallLogService modelCallLogService;

    public EmbedResult embed(Long modelConfigId, List<String> texts, Long userId, String summary) {
        if (texts == null || texts.isEmpty()) {
            throw BusinessException.badRequest("没有需要向量化的文本");
        }
        ModelRuntime runtime = modelRuntimeFactory.resolveForExecution(modelConfigId);
        if (runtime.modelType() != ModelType.EMBEDDING) {
            throw BusinessException.badRequest("知识库必须绑定 Embedding 类型模型");
        }
        EmbeddingModel embeddingModel = modelAdapterRegistry.embeddingModel(runtime);
        List<float[]> vectors = new ArrayList<>(texts.size());
        int promptTokens = 0;
        boolean estimated = false;
        Integer dimension = null;

        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(from, to);
            List<TextSegment> segments = batch.stream().map(TextSegment::from).toList();
            long start = System.currentTimeMillis();
            try {
                Response<List<Embedding>> response = embeddingModel.embedAll(segments);
                List<Embedding> batchEmbeddings = response.content();
                if (batchEmbeddings == null || batchEmbeddings.size() != batch.size()) {
                    throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                            "Embedding 返回数量与请求不一致");
                }
                int batchPrompt = 0;
                boolean batchEstimated = false;
                TokenUsage usage = response.tokenUsage();
                if (usage != null && usage.inputTokenCount() != null) {
                    batchPrompt = usage.inputTokenCount();
                } else {
                    batchPrompt = TokenEstimator.estimate(String.join("\n", batch));
                    batchEstimated = true;
                    estimated = true;
                }
                promptTokens += batchPrompt;

                for (Embedding embedding : batchEmbeddings) {
                    float[] vector = embedding.vector();
                    if (vector == null || vector.length == 0) {
                        throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                                "Embedding 返回了空向量");
                    }
                    if (dimension == null) {
                        dimension = vector.length;
                    } else if (dimension != vector.length) {
                        throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                                "同一批次 Embedding 向量维度不一致");
                    }
                    vectors.add(vector);
                }
                long latency = System.currentTimeMillis() - start;
                recordCall(runtime, userId, summary, true, batchPrompt, latency, batchEstimated, null);
            } catch (BusinessException ex) {
                long latency = System.currentTimeMillis() - start;
                recordCall(runtime, userId, summary, false, null, latency, true, ex.getMessage());
                throw ex;
            } catch (Exception ex) {
                long latency = System.currentTimeMillis() - start;
                String message = safeMessage(ex);
                recordCall(runtime, userId, summary, false, null, latency, true, message);
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                        "Embedding 调用失败：" + message);
            }
        }

        return new EmbedResult(vectors, dimension == null ? 0 : dimension, promptTokens, estimated);
    }

    private void recordCall(ModelRuntime runtime, Long userId, String summary, boolean success,
                            Integer promptTokens, long latency, boolean estimated, String error) {
        modelCallLogService.record(ModelCallLogCommand.builder()
                .traceId(MDC.get("traceId"))
                .userId(userId)
                .modelConfigId(runtime.configId())
                .providerCode(runtime.providerCode())
                .modelName(runtime.modelName())
                .requestType(ModelCallType.RAG_EMBEDDING.name())
                .promptTokens(promptTokens)
                .completionTokens(0)
                .totalTokens(promptTokens)
                .latencyMs(latency)
                .success(success)
                .errorCode(success ? null : "RAG_EMBEDDING_FAILED")
                .errorMessage(error)
                .estimated(estimated)
                .inputPricePerThousand(runtime.inputPricePerThousand())
                .outputPricePerThousand(runtime.outputPricePerThousand())
                .currency(runtime.currency())
                .requestSummary(summary)
                .build());
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    public record EmbedResult(
            List<float[]> vectors,
            int dimension,
            int promptTokens,
            boolean estimated
    ) {
    }
}
