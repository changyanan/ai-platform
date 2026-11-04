package com.aiplatform.model.service.impl;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.PageResult;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.common.security.SecurityUtils;
import com.aiplatform.model.adapter.ModelAdapterRegistry;
import com.aiplatform.model.crypto.SecretCryptoService;
import com.aiplatform.model.dto.ModelConfigQuery;
import com.aiplatform.model.dto.ModelConfigRequest;
import com.aiplatform.model.entity.ModelConfig;
import com.aiplatform.model.entity.ModelProvider;
import com.aiplatform.model.enums.ConnectionStatus;
import com.aiplatform.model.enums.ModelCallType;
import com.aiplatform.model.enums.ModelCapability;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.mapper.ModelConfigMapper;
import com.aiplatform.model.mapper.ModelProviderMapper;
import com.aiplatform.model.runtime.ModelRuntime;
import com.aiplatform.model.service.ModelCallLogCommand;
import com.aiplatform.model.service.ModelCallLogService;
import com.aiplatform.model.service.ModelConfigService;
import com.aiplatform.model.service.ModelRuntimeFactory;
import com.aiplatform.model.vo.ModelConfigVO;
import com.aiplatform.model.vo.ModelOptionVO;
import com.aiplatform.model.vo.ModelProviderVO;
import com.aiplatform.model.vo.ModelTestResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private static final long TEST_TIMEOUT_SECONDS = 45;
    private static final String TEST_PROMPT = "请只回复：pong";

    private final ModelConfigMapper modelConfigMapper;
    private final ModelProviderMapper modelProviderMapper;
    private final ModelRuntimeFactory runtimeFactory;
    private final SecretCryptoService secretCryptoService;
    private final ModelAdapterRegistry adapterRegistry;
    private final ModelCallLogService modelCallLogService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<ModelConfigVO> page(ModelConfigQuery query) {
        LambdaQueryWrapper<ModelConfig> wrapper = Wrappers.<ModelConfig>lambdaQuery()
                .orderByDesc(ModelConfig::getIsDefault)
                .orderByDesc(ModelConfig::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(ModelConfig::getDisplayName, keyword)
                    .or().like(ModelConfig::getModelName, keyword));
        }
        if (query.getModelType() != null) {
            wrapper.eq(ModelConfig::getModelType, query.getModelType().name());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(ModelConfig::getEnabled, query.getEnabled());
        }
        if (query.getProviderId() != null) {
            wrapper.eq(ModelConfig::getProviderId, query.getProviderId());
        }
        Page<ModelConfig> page = modelConfigMapper.selectPage(
                new Page<>(query.currentPage(), query.pageSize()), wrapper);
        Map<Long, ModelProvider> providers = providerMap();
        List<ModelConfigVO> records = page.getRecords().stream()
                .map(config -> toVO(config, providers.get(config.getProviderId())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<ModelProviderVO> providers() {
        return modelProviderMapper.selectList(Wrappers.<ModelProvider>lambdaQuery()
                        .orderByAsc(ModelProvider::getId))
                .stream()
                .map(ModelProviderVO::from)
                .toList();
    }

    @Override
    public List<ModelOptionVO> options() {
        Map<Long, ModelProvider> providers = providerMap();
        return modelConfigMapper.selectList(Wrappers.<ModelConfig>lambdaQuery()
                        .eq(ModelConfig::getEnabled, true)
                        .orderByDesc(ModelConfig::getIsDefault)
                        .orderByAsc(ModelConfig::getId))
                .stream()
                .filter(config -> providers.containsKey(config.getProviderId()))
                .map(config -> {
                    ModelProvider provider = providers.get(config.getProviderId());
                    String reason = runtimeFactory.unavailableReason(config, provider);
                    return new ModelOptionVO(
                            config.getId(),
                            config.getDisplayName(),
                            config.getModelName(),
                            ModelType.valueOf(config.getModelType()),
                            provider.getProviderName(),
                            runtimeFactory.parseCapabilities(config.getCapabilities()),
                            reason == null,
                            reason,
                            Boolean.TRUE.equals(config.getIsDefault()),
                            config.getTemperature() == null ? 0.7 : config.getTemperature().doubleValue(),
                            config.getMaxTokens(),
                            config.getContextWindow(),
                            connectionStatus(config, provider)
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public ModelConfigVO create(ModelConfigRequest request) {
        ModelProvider provider = requireProvider(request.providerId());
        ValidatedFields fields = validateRequest(request, provider);
        ensureModelNameUnique(provider.getId(), request.modelName(), null);

        ModelConfig config = new ModelConfig();
        config.setProviderId(provider.getId());
        applyFields(config, request, fields, provider, true);
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefault(fields.modelType());
        }
        config.setLastTestStatus(initialTestStatus(provider, config.getApiKeyCipher()));
        config.setLastTestMessage(initialTestMessage(provider, config.getApiKeyCipher()));
        modelConfigMapper.insert(config);
        return toVO(modelConfigMapper.selectById(config.getId()), provider);
    }

    @Override
    @Transactional
    public ModelConfigVO update(Long id, ModelConfigRequest request) {
        ModelConfig existing = requireConfig(id);
        ModelProvider provider = requireProvider(request.providerId());
        ValidatedFields fields = validateRequest(request, provider);
        ensureModelNameUnique(provider.getId(), request.modelName(), id);

        String apiKeyCipher = existing.getApiKeyCipher();
        String apiKeyMask = existing.getApiKeyMask();
        if (Boolean.TRUE.equals(request.clearApiKey())) {
            apiKeyCipher = null;
            apiKeyMask = null;
        } else if (StringUtils.hasText(request.apiKey())) {
            String trimmedKey = request.apiKey().trim();
            apiKeyCipher = secretCryptoService.encrypt(trimmedKey);
            apiKeyMask = secretCryptoService.mask(trimmedKey);
        }

        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefault(fields.modelType());
        }

        LocalDateTime now = LocalDateTime.now();
        String testStatus = initialTestStatus(provider, apiKeyCipher);
        String testMessage = initialTestMessage(provider, apiKeyCipher);
        modelConfigMapper.update(null, new LambdaUpdateWrapper<ModelConfig>()
                .eq(ModelConfig::getId, id)
                .set(ModelConfig::getProviderId, provider.getId())
                .set(ModelConfig::getDisplayName, request.displayName().trim())
                .set(ModelConfig::getModelName, request.modelName().trim())
                .set(ModelConfig::getModelType, fields.modelType().name())
                .set(ModelConfig::getApiBaseUrl, normalizeOptional(request.apiBaseUrl()))
                .set(ModelConfig::getApiKeyCipher, apiKeyCipher)
                .set(ModelConfig::getApiKeyMask, apiKeyMask)
                .set(ModelConfig::getTemperature, request.temperature())
                .set(ModelConfig::getMaxTokens, fields.maxTokens())
                .set(ModelConfig::getContextWindow, request.contextWindow())
                .set(ModelConfig::getEmbeddingDimension, fields.embeddingDimension())
                .set(ModelConfig::getCapabilities, fields.capabilitiesJson())
                .set(ModelConfig::getInputPricePer1k, fields.inputPrice())
                .set(ModelConfig::getOutputPricePer1k, fields.outputPrice())
                .set(ModelConfig::getCurrency, fields.currency())
                .set(ModelConfig::getEnabled, request.enabled() == null || request.enabled())
                .set(ModelConfig::getIsDefault, Boolean.TRUE.equals(request.isDefault()))
                .set(ModelConfig::getLastTestStatus, testStatus)
                .set(ModelConfig::getLastTestMessage, testMessage)
                .set(ModelConfig::getLastTestAt, null)
                .set(ModelConfig::getUpdatedAt, now));

        log.info("Model config {} updated by {}", id, SecurityUtils.currentUsername());
        return toVO(modelConfigMapper.selectById(id), provider);
    }

    @Override
    public void delete(Long id) {
        ModelConfig config = requireConfig(id);
        modelConfigMapper.deleteById(id);
        log.info("Model config {} ({}) soft-deleted by {}", id, config.getDisplayName(),
                SecurityUtils.currentUsername());
    }

    @Override
    public void setEnabled(Long id, boolean enabled) {
        requireConfig(id);
        modelConfigMapper.update(null, Wrappers.<ModelConfig>lambdaUpdate()
                .eq(ModelConfig::getId, id)
                .set(ModelConfig::getEnabled, enabled));
    }

    @Override
    public ModelTestResultVO testConnection(Long id) {
        ModelConfig config = requireConfig(id);
        ModelProvider provider = requireProvider(config.getProviderId());
        LocalDateTime testedAt = LocalDateTime.now();

        if (Boolean.TRUE.equals(provider.getRequiresApiKey()) && !StringUtils.hasText(config.getApiKeyCipher())) {
            updateTestStatus(id, ConnectionStatus.NOT_CONFIGURED, "尚未配置 API Key，无法发起真实连接测试", null);
            return new ModelTestResultVO(false, ConnectionStatus.NOT_CONFIGURED.name(),
                    "尚未配置 API Key，无法发起真实连接测试", 0, config.getModelName(), null, null, testedAt);
        }

        ModelType modelType = ModelType.valueOf(config.getModelType());
        if (modelType == ModelType.ASR) {
            return new ModelTestResultVO(false, ConnectionStatus.UNTESTED.name(),
                    "ASR 模型需通过多模态中心上传音频进行测试；通用文本探针不适用于语音模型",
                    0, config.getModelName(), null, null, testedAt);
        }

        ModelRuntime runtime;
        try {
            runtime = runtimeFactory.resolveForTest(id, TEST_TIMEOUT_SECONDS)
                    .withMaxTokensAndTemperature(32, 0.0);
        } catch (Exception ex) {
            String message = safeError(ex);
            updateTestStatus(id, ConnectionStatus.FAILED, message, null);
            return new ModelTestResultVO(false, ConnectionStatus.FAILED.name(), message, 0,
                    config.getModelName(), null, null, testedAt);
        }

        long start = System.currentTimeMillis();
        try {
            Integer promptTokens = null;
            Integer completionTokens = null;
            String successDetail;
            if (modelType == ModelType.EMBEDDING) {
                EmbeddingModel embeddingModel = adapterRegistry.embeddingModel(runtime);
                var embeddingResponse = embeddingModel.embed("AI platform connectivity test");
                if (embeddingResponse == null || embeddingResponse.content() == null
                        || embeddingResponse.content().vector() == null
                        || embeddingResponse.content().vector().length == 0) {
                    throw new IllegalStateException("Embedding 返回为空");
                }
                successDetail = "连接成功 · 向量维度 " + embeddingResponse.content().vector().length;
            } else {
                ChatModel chatModel = modelType == ModelType.VISION
                        ? adapterRegistry.visionModel(runtime)
                        : adapterRegistry.chatModel(runtime);
                ChatResponse response = chatModel.chat(List.of(UserMessage.from(TEST_PROMPT)));
                TokenUsage usage = response.tokenUsage();
                if (usage != null) {
                    promptTokens = usage.inputTokenCount();
                    completionTokens = usage.outputTokenCount();
                }
                String text = response.aiMessage() == null ? null : response.aiMessage().text();
                successDetail = StringUtils.hasText(text)
                        ? "连接成功 · 模型响应：" + limit(text.trim(), 80)
                        : "连接成功";
            }
            long latency = System.currentTimeMillis() - start;
            updateTestStatus(id, ConnectionStatus.SUCCESS, successDetail, testedAt);
            recordCallLog(config, runtime, true, latency, promptTokens, completionTokens, null);
            return new ModelTestResultVO(true, ConnectionStatus.SUCCESS.name(), successDetail,
                    latency, config.getModelName(), promptTokens, completionTokens, testedAt);
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            String message = safeError(ex);
            log.warn("Model connectivity test failed for config {}: {}", id, message);
            updateTestStatus(id, ConnectionStatus.FAILED, message, testedAt);
            recordCallLog(config, runtime, false, latency, null, null, message);
            return new ModelTestResultVO(false, ConnectionStatus.FAILED.name(), message,
                    latency, config.getModelName(), null, null, testedAt);
        }
    }

    private void recordCallLog(ModelConfig config, ModelRuntime runtime, boolean success, long latency,
                               Integer promptTokens, Integer completionTokens, String errorMessage) {
        Integer totalTokens = promptTokens != null && completionTokens != null
                ? promptTokens + completionTokens : null;
        modelCallLogService.record(ModelCallLogCommand.builder()
                .traceId(MDC.get("traceId"))
                .userId(SecurityUtils.currentUserId())
                .modelConfigId(config.getId())
                .providerCode(runtime.providerCode())
                .modelName(runtime.modelName())
                .requestType(ModelCallType.CONNECTIVITY.name())
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .latencyMs(latency)
                .success(success)
                .errorCode(success ? null : "CONNECTIVITY_FAILED")
                .errorMessage(errorMessage)
                .estimated(false)
                .inputPricePerThousand(runtime.inputPricePerThousand())
                .outputPricePerThousand(runtime.outputPricePerThousand())
                .currency(runtime.currency())
                .requestSummary("connectivity test · " + runtime.modelName())
                .build());
    }

    private ValidatedFields validateRequest(ModelConfigRequest request, ModelProvider provider) {
        ModelType type = request.modelType();
        if (type == ModelType.EMBEDDING && (request.embeddingDimension() == null || request.embeddingDimension() <= 0)) {
            throw BusinessException.badRequest("Embedding 模型必须配置向量维度");
        }
        List<String> capabilities = normalizeCapabilities(request.capabilities(), type);
        String capabilitiesJson;
        try {
            capabilitiesJson = objectMapper.writeValueAsString(capabilities);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型能力序列化失败");
        }

        String baseUrl = normalizeOptional(request.apiBaseUrl());
        if (baseUrl != null) {
            validateUrl(baseUrl);
        }

        BigDecimal inputPrice = request.inputPricePer1k();
        BigDecimal outputPrice = request.outputPricePer1k();
        if ((inputPrice == null) != (outputPrice == null)) {
            throw BusinessException.badRequest("输入单价与输出单价必须同时配置，或同时留空");
        }
        String currency = inputPrice == null ? null
                : StringUtils.hasText(request.currency()) ? request.currency() : "CNY";

        Integer maxTokens = request.maxTokens();
        if ((type == ModelType.TEXT || type == ModelType.VISION) && maxTokens == null) {
            maxTokens = 4096;
        }
        Integer embeddingDimension = type == ModelType.EMBEDDING ? request.embeddingDimension() : null;

        return new ValidatedFields(type, maxTokens, embeddingDimension, capabilitiesJson,
                inputPrice, outputPrice, currency);
    }

    private List<String> normalizeCapabilities(List<String> requested, ModelType type) {
        List<String> values = requested == null ? new ArrayList<>() : new ArrayList<>(requested);
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                normalized.add(ModelCapability.valueOf(value.trim().toUpperCase(Locale.ROOT)).name());
            } catch (IllegalArgumentException ex) {
                throw BusinessException.badRequest("不支持的能力标签：" + value);
            }
        }
        normalized.add(type.name());
        return normalized.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private void validateUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("unsupported url");
            }
        } catch (Exception ex) {
            throw BusinessException.badRequest("API Base URL 不合法，仅支持 http/https");
        }
    }

    private void applyFields(ModelConfig config, ModelConfigRequest request, ValidatedFields fields,
                             ModelProvider provider, boolean create) {
        config.setDisplayName(request.displayName().trim());
        config.setModelName(request.modelName().trim());
        config.setModelType(fields.modelType().name());
        config.setApiBaseUrl(normalizeOptional(request.apiBaseUrl()));
        if (StringUtils.hasText(request.apiKey())) {
            String trimmedKey = request.apiKey().trim();
            config.setApiKeyCipher(secretCryptoService.encrypt(trimmedKey));
            config.setApiKeyMask(secretCryptoService.mask(trimmedKey));
        }
        config.setTemperature(request.temperature());
        config.setMaxTokens(fields.maxTokens());
        config.setContextWindow(request.contextWindow());
        config.setEmbeddingDimension(fields.embeddingDimension());
        config.setCapabilities(fields.capabilitiesJson());
        config.setInputPricePer1k(fields.inputPrice());
        config.setOutputPricePer1k(fields.outputPrice());
        config.setCurrency(fields.currency());
        config.setEnabled(request.enabled() == null || request.enabled());
        config.setIsDefault(Boolean.TRUE.equals(request.isDefault()));
    }

    private String initialTestStatus(ModelProvider provider, String apiKeyCipher) {
        if (Boolean.TRUE.equals(provider.getRequiresApiKey()) && !StringUtils.hasText(apiKeyCipher)) {
            return ConnectionStatus.NOT_CONFIGURED.name();
        }
        return ConnectionStatus.UNTESTED.name();
    }

    private String initialTestMessage(ModelProvider provider, String apiKeyCipher) {
        if (Boolean.TRUE.equals(provider.getRequiresApiKey()) && !StringUtils.hasText(apiKeyCipher)) {
            return "尚未配置 API Key";
        }
        return "配置已保存，等待连通性测试";
    }

    private void updateTestStatus(Long id, ConnectionStatus status, String message, LocalDateTime testedAt) {
        modelConfigMapper.update(null, Wrappers.<ModelConfig>lambdaUpdate()
                .eq(ModelConfig::getId, id)
                .set(ModelConfig::getLastTestStatus, status.name())
                .set(ModelConfig::getLastTestMessage, limit(message, 1000))
                .set(ModelConfig::getLastTestAt, testedAt));
    }

    private ModelConfigVO toVO(ModelConfig config, ModelProvider provider) {
        if (config == null || provider == null) {
            throw BusinessException.notFound("模型配置不存在");
        }
        String reason = runtimeFactory.unavailableReason(config, provider);
        return new ModelConfigVO(
                config.getId(),
                provider.getId(),
                provider.getProviderCode(),
                provider.getProviderName(),
                config.getDisplayName(),
                config.getModelName(),
                ModelType.valueOf(config.getModelType()),
                config.getApiBaseUrl(),
                runtimeFactory.resolveBaseUrl(config, provider),
                config.getApiKeyMask(),
                StringUtils.hasText(config.getApiKeyCipher()),
                config.getTemperature(),
                config.getMaxTokens(),
                config.getContextWindow(),
                config.getEmbeddingDimension(),
                runtimeFactory.parseCapabilities(config.getCapabilities()),
                config.getInputPricePer1k(),
                config.getOutputPricePer1k(),
                config.getCurrency(),
                Boolean.TRUE.equals(config.getEnabled()),
                Boolean.TRUE.equals(config.getIsDefault()),
                connectionStatus(config, provider),
                config.getLastTestMessage(),
                config.getLastTestAt(),
                reason == null,
                reason,
                config.getCreatedAt()
        );
    }

    private String connectionStatus(ModelConfig config, ModelProvider provider) {
        if (Boolean.TRUE.equals(provider.getRequiresApiKey()) && !StringUtils.hasText(config.getApiKeyCipher())) {
            return ConnectionStatus.NOT_CONFIGURED.name();
        }
        return StringUtils.hasText(config.getLastTestStatus())
                ? config.getLastTestStatus()
                : ConnectionStatus.UNTESTED.name();
    }

    private void clearDefault(ModelType modelType) {
        modelConfigMapper.update(null, Wrappers.<ModelConfig>lambdaUpdate()
                .eq(ModelConfig::getModelType, modelType.name())
                .eq(ModelConfig::getIsDefault, true)
                .set(ModelConfig::getIsDefault, false));
    }

    private void ensureModelNameUnique(Long providerId, String modelName, Long excludeId) {
        LambdaQueryWrapper<ModelConfig> wrapper = Wrappers.<ModelConfig>lambdaQuery()
                .eq(ModelConfig::getProviderId, providerId)
                .eq(ModelConfig::getModelName, modelName.trim());
        if (excludeId != null) {
            wrapper.ne(ModelConfig::getId, excludeId);
        }
        if (modelConfigMapper.selectCount(wrapper) > 0) {
            throw BusinessException.conflict("该供应商下已存在同名模型配置");
        }
    }

    private ModelProvider requireProvider(Long providerId) {
        ModelProvider provider = modelProviderMapper.selectById(providerId);
        if (provider == null) {
            throw BusinessException.notFound("模型供应商不存在");
        }
        return provider;
    }

    private ModelConfig requireConfig(Long id) {
        ModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            throw BusinessException.notFound("模型配置不存在");
        }
        return config;
    }

    private Map<Long, ModelProvider> providerMap() {
        return modelProviderMapper.selectList(Wrappers.<ModelProvider>lambdaQuery())
                .stream()
                .collect(Collectors.toMap(ModelProvider::getId, Function.identity()));
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeError(Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            message = ex.getClass().getSimpleName();
        }
        String sanitized = message
                .replaceAll("(?i)(sk-|api[_-]?key[\"'=:\\s]+)[A-Za-z0-9_\\-]{6,}", "$1***")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return limit(sanitized, 500);
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private record ValidatedFields(
            ModelType modelType,
            Integer maxTokens,
            Integer embeddingDimension,
            String capabilitiesJson,
            BigDecimal inputPrice,
            BigDecimal outputPrice,
            String currency
    ) {
    }
}
