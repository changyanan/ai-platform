package com.aiplatform.multimodal.service.impl;

import com.aiplatform.common.ai.TokenEstimator;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.model.adapter.AsrTranscription;
import com.aiplatform.model.adapter.ModelAdapterRegistry;
import com.aiplatform.model.enums.ModelCallType;
import com.aiplatform.model.enums.ModelCapability;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.runtime.ModelRuntime;
import com.aiplatform.model.service.ModelCallLogCommand;
import com.aiplatform.model.service.ModelCallLogService;
import com.aiplatform.model.service.ModelRuntimeFactory;
import com.aiplatform.multimodal.document.DocumentTextExtractor;
import com.aiplatform.multimodal.entity.FileAsset;
import com.aiplatform.multimodal.entity.MultimodalTask;
import com.aiplatform.multimodal.mapper.FileAssetMapper;
import com.aiplatform.multimodal.mapper.MultimodalTaskMapper;
import com.aiplatform.multimodal.service.FileAssetService;
import com.aiplatform.multimodal.service.MultimodalService;
import com.aiplatform.multimodal.vo.FileAssetVO;
import com.aiplatform.multimodal.vo.MultimodalTaskVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultimodalServiceImpl implements MultimodalService {

    private static final int MAX_CONTEXT_CHARS = 12_000;

    private final FileAssetService fileAssetService;
    private final FileAssetMapper fileAssetMapper;
    private final MultimodalTaskMapper taskMapper;
    private final DocumentTextExtractor textExtractor;
    private final ModelRuntimeFactory modelRuntimeFactory;
    private final ModelAdapterRegistry modelAdapterRegistry;
    private final ModelCallLogService modelCallLogService;

    @Override
    public MultimodalTaskVO process(MultipartFile file, String requestedTaskType, Long modelId, String customPrompt) {
        FileAssetVO uploaded = fileAssetService.upload(file);
        FileAsset asset = fileAssetMapper.selectById(uploaded.id());
        if (asset == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件资产保存异常");
        }
        String taskType = resolveTaskType(asset.getFileCategory(), requestedTaskType);
        validateTaskType(asset.getFileCategory(), taskType);

        MultimodalTask task = new MultimodalTask();
        task.setUserId(SecurityUtils.currentUserId());
        task.setFileAssetId(asset.getId());
        task.setFileName(asset.getOriginalName());
        task.setFileCategory(asset.getFileCategory());
        task.setTaskType(taskType);
        task.setStatus("PROCESSING");
        task.setTokenEstimated(false);
        task.setCreatedAt(LocalDateTime.now());
        if (modelId != null) {
            var config = modelRuntimeFactory.findConfig(modelId);
            task.setModelConfigId(modelId);
            task.setModelName(config == null ? null : config.getDisplayName());
        }
        taskMapper.insert(task);

        long start = System.currentTimeMillis();
        try {
            AnalysisResult result = switch (taskType) {
                case "OCR", "IMAGE_UNDERSTANDING" -> analyzeImage(asset, taskType, modelId, customPrompt);
                case "DOCUMENT_EXTRACT", "DOCUMENT_SUMMARY" ->
                        analyzeDocument(asset, taskType, modelId, customPrompt);
                case "AUDIO_TRANSCRIBE" -> transcribeAudio(asset, modelId);
                default -> throw new BusinessException(ErrorCode.MULTIMODAL_UNAVAILABLE,
                        "不支持的多模态任务类型：" + taskType);
            };
            long latency = System.currentTimeMillis() - start;
            taskMapper.update(null, Wrappers.<MultimodalTask>lambdaUpdate()
                    .eq(MultimodalTask::getId, task.getId())
                    .set(MultimodalTask::getStatus, "SUCCESS")
                    .set(MultimodalTask::getExtractedText, limit(result.extractedText(), 500_000))
                    .set(MultimodalTask::getResultText, limit(result.resultText(), 500_000))
                    .set(MultimodalTask::getPromptTokens, result.promptTokens())
                    .set(MultimodalTask::getCompletionTokens, result.completionTokens())
                    .set(MultimodalTask::getTotalTokens, result.totalTokens())
                    .set(MultimodalTask::getTokenEstimated, result.estimated())
                    .set(MultimodalTask::getLatencyMs, latency)
                    .set(MultimodalTask::getFinishedAt, LocalDateTime.now()));
            return MultimodalTaskVO.from(taskMapper.selectById(task.getId()));
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            String message = safeError(ex);
            taskMapper.update(null, Wrappers.<MultimodalTask>lambdaUpdate()
                    .eq(MultimodalTask::getId, task.getId())
                    .set(MultimodalTask::getStatus, "FAILED")
                    .set(MultimodalTask::getErrorMessage, message)
                    .set(MultimodalTask::getLatencyMs, latency)
                    .set(MultimodalTask::getFinishedAt, LocalDateTime.now()));
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.MULTIMODAL_UNAVAILABLE, "多模态处理失败：" + message);
        }
    }

    @Override
    public List<MultimodalTaskVO> recent(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        return taskMapper.selectList(Wrappers.<MultimodalTask>lambdaQuery()
                        .eq(MultimodalTask::getUserId, SecurityUtils.currentUserId())
                        .orderByDesc(MultimodalTask::getId)
                        .last("LIMIT " + size))
                .stream().map(MultimodalTaskVO::from).toList();
    }

    @Override
    public MultimodalTaskVO get(Long id) {
        MultimodalTask task = taskMapper.selectById(id);
        if (task == null || !task.getUserId().equals(SecurityUtils.currentUserId())) {
            throw BusinessException.notFound("多模态任务不存在");
        }
        return MultimodalTaskVO.from(task);
    }

    private AnalysisResult analyzeImage(FileAsset asset, String taskType, Long modelId, String customPrompt) {
        if (modelId == null) {
            throw new BusinessException(ErrorCode.MULTIMODAL_UNAVAILABLE, "图片任务必须选择视觉模型");
        }
        ModelRuntime runtime = modelRuntimeFactory.resolveForExecution(modelId);
        if (runtime.modelType() != ModelType.VISION && !runtime.supports(ModelCapability.VISION)) {
            throw new BusinessException(ErrorCode.MULTIMODAL_UNAVAILABLE,
                    "所选模型未声明 VISION 能力，不能处理图片");
        }
        byte[] bytes = fileAssetService.readBytes(asset);
        String prompt = StringUtils.hasText(customPrompt) ? customPrompt.trim()
                : ("OCR".equals(taskType)
                ? "请逐字提取图片中的所有文字。保持原有段落，无法识别的字符用 [无法识别] 标记，不要编造。"
                : "请详细描述这张图片的内容、关键信息与可能的业务含义。使用简体中文，不要编造看不见的内容。");
        String base64 = Base64.getEncoder().encodeToString(bytes);
        UserMessage message = UserMessage.from(prompt,
                ImageContent.from(base64, asset.getMimeType(), ImageContent.DetailLevel.HIGH));
        long start = System.currentTimeMillis();
        try {
            ChatModel model = modelAdapterRegistry.visionModel(runtime);
            ChatResponse response = model.chat(message);
            String text = response.aiMessage() == null ? "" : response.aiMessage().text();
            TokenUsage usage = response.tokenUsage();
            int promptTokens;
            int completionTokens;
            boolean estimated = false;
            if (usage != null && usage.inputTokenCount() != null && usage.outputTokenCount() != null) {
                promptTokens = usage.inputTokenCount();
                completionTokens = usage.outputTokenCount();
            } else {
                promptTokens = TokenEstimator.estimate(prompt);
                completionTokens = TokenEstimator.estimate(text);
                estimated = true;
            }
            long latency = System.currentTimeMillis() - start;
            recordCall(runtime, taskType, true, promptTokens, completionTokens, latency, estimated, null);
            return new AnalysisResult(null, text, promptTokens, completionTokens,
                    promptTokens + completionTokens, estimated);
        } catch (RuntimeException ex) {
            long latency = System.currentTimeMillis() - start;
            recordCall(runtime, taskType, false, null, null, latency, true, safeError(ex));
            throw ex;
        }
    }

    private AnalysisResult analyzeDocument(FileAsset asset, String taskType, Long modelId, String customPrompt) {
        byte[] bytes = fileAssetService.readBytes(asset);
        String text = textExtractor.extract(asset.getExtension(), bytes);
        String context = limit(text, MAX_CONTEXT_CHARS);
        if (modelId == null) {
            return new AnalysisResult(text, text, null, null, null, false);
        }
        ModelRuntime runtime = modelRuntimeFactory.resolveForExecution(modelId);
        if (runtime.modelType() != ModelType.TEXT && runtime.modelType() != ModelType.VISION) {
            throw new BusinessException(ErrorCode.MULTIMODAL_UNAVAILABLE,
                    "文档任务需要文本或视觉模型");
        }
        String instruction = StringUtils.hasText(customPrompt) ? customPrompt.trim()
                : ("DOCUMENT_SUMMARY".equals(taskType)
                ? "请用简体中文总结以下文档，输出关键结论、风险与待办事项。不得编造文档未提及的信息。"
                : "请从以下文档中提取关键字段、事实与可执行结论。不得编造文档未提及的信息。");
        String prompt = instruction + "\n\n文档内容：\n" + context;
        long start = System.currentTimeMillis();
        try {
            ChatModel model = modelAdapterRegistry.chatModel(runtime);
            ChatResponse response = model.chat(UserMessage.from(prompt));
            String answer = response.aiMessage() == null ? "" : response.aiMessage().text();
            TokenUsage usage = response.tokenUsage();
            int promptTokens;
            int completionTokens;
            boolean estimated = false;
            if (usage != null && usage.inputTokenCount() != null && usage.outputTokenCount() != null) {
                promptTokens = usage.inputTokenCount();
                completionTokens = usage.outputTokenCount();
            } else {
                promptTokens = TokenEstimator.estimate(prompt);
                completionTokens = TokenEstimator.estimate(answer);
                estimated = true;
            }
            long latency = System.currentTimeMillis() - start;
            recordCall(runtime, taskType, true, promptTokens, completionTokens, latency, estimated, null);
            return new AnalysisResult(text, answer, promptTokens, completionTokens,
                    promptTokens + completionTokens, estimated);
        } catch (RuntimeException ex) {
            long latency = System.currentTimeMillis() - start;
            recordCall(runtime, taskType, false, null, null, latency, true, safeError(ex));
            throw ex;
        }
    }

    private AnalysisResult transcribeAudio(FileAsset asset, Long modelId) {
        if (modelId == null) {
            throw new BusinessException(ErrorCode.MULTIMODAL_UNAVAILABLE, "语音任务必须选择 ASR 模型");
        }
        ModelRuntime runtime = modelRuntimeFactory.resolveForExecution(modelId);
        if (runtime.modelType() != ModelType.ASR && !runtime.supports(ModelCapability.ASR)) {
            throw new BusinessException(ErrorCode.MULTIMODAL_UNAVAILABLE,
                    "所选模型未声明 ASR 能力，不能转录音频");
        }
        byte[] bytes = fileAssetService.readBytes(asset);
        long start = System.currentTimeMillis();
        try {
            AsrTranscription transcription = modelAdapterRegistry.asrAdapter(runtime)
                    .transcribe(runtime, bytes, asset.getOriginalName(), asset.getMimeType());
            long latency = System.currentTimeMillis() - start;
            recordCall(runtime, "AUDIO_TRANSCRIBE", true, null, null, latency, true, null);
            return new AnalysisResult(transcription.text(), transcription.text(),
                    null, null, null, true);
        } catch (RuntimeException ex) {
            long latency = System.currentTimeMillis() - start;
            recordCall(runtime, "AUDIO_TRANSCRIBE", false, null, null, latency, true, safeError(ex));
            throw ex;
        }
    }

    private void recordCall(ModelRuntime runtime, String taskType, boolean success,
                            Integer promptTokens, Integer completionTokens, long latency,
                            boolean estimated, String error) {
        String type = switch (taskType) {
            case "AUDIO_TRANSCRIBE" -> ModelCallType.ASR.name();
            case "DOCUMENT_EXTRACT", "DOCUMENT_SUMMARY" -> ModelCallType.DOCUMENT_ANALYSIS.name();
            default -> ModelCallType.VISION.name();
        };
        modelCallLogService.record(ModelCallLogCommand.builder()
                .traceId(MDC.get("traceId"))
                .userId(SecurityUtils.currentUserId())
                .modelConfigId(runtime.configId())
                .providerCode(runtime.providerCode())
                .modelName(runtime.modelName())
                .requestType(type)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens == null || completionTokens == null
                        ? null : promptTokens + completionTokens)
                .latencyMs(latency)
                .success(success)
                .errorCode(success ? null : "MULTIMODAL_FAILED")
                .errorMessage(error)
                .estimated(estimated)
                .inputPricePerThousand(runtime.inputPricePerThousand())
                .outputPricePerThousand(runtime.outputPricePerThousand())
                .currency(runtime.currency())
                .requestSummary("multimodal · " + taskType)
                .build());
    }

    private String resolveTaskType(String category, String requested) {
        if (StringUtils.hasText(requested)) {
            try {
                return requested.trim().toUpperCase(Locale.ROOT);
            } catch (Exception ex) {
                throw BusinessException.badRequest("任务类型不合法");
            }
        }
        return switch (category) {
            case "IMAGE" -> "IMAGE_UNDERSTANDING";
            case "AUDIO" -> "AUDIO_TRANSCRIBE";
            default -> "DOCUMENT_EXTRACT";
        };
    }

    private void validateTaskType(String category, String taskType) {
        boolean valid = switch (taskType) {
            case "OCR", "IMAGE_UNDERSTANDING" -> "IMAGE".equals(category);
            case "DOCUMENT_EXTRACT", "DOCUMENT_SUMMARY" ->
                    "DOCUMENT".equals(category) || "TEXT".equals(category);
            case "AUDIO_TRANSCRIBE" -> "AUDIO".equals(category);
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.MULTIMODAL_UNAVAILABLE,
                    "任务类型 " + taskType + " 与文件类型 " + category + " 不匹配");
        }
    }

    private String safeError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() > 1900 ? message.substring(0, 1900) : message;
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private record AnalysisResult(
            String extractedText,
            String resultText,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            boolean estimated
    ) {
    }
}
