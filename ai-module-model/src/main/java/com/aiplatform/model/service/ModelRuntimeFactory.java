package com.aiplatform.model.service;

import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.exception.BusinessException;
import com.aiplatform.model.crypto.SecretCryptoService;
import com.aiplatform.model.entity.ModelConfig;
import com.aiplatform.model.entity.ModelProvider;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.enums.ProviderProtocol;
import com.aiplatform.model.mapper.ModelConfigMapper;
import com.aiplatform.model.mapper.ModelProviderMapper;
import com.aiplatform.model.runtime.ModelRuntime;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 模型配置到运行时参数的唯一转换入口。
 * 所有解密仅在此处发生，运行时对象不会序列化到 API 或日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRuntimeFactory {

    private static final long CHAT_TIMEOUT_SECONDS = 180;
    private static final long EMBEDDING_TIMEOUT_SECONDS = 60;

    private final ModelConfigMapper modelConfigMapper;
    private final ModelProviderMapper modelProviderMapper;
    private final SecretCryptoService secretCryptoService;
    private final ObjectMapper objectMapper;

    public ModelConfig findConfig(Long configId) {
        return modelConfigMapper.selectById(configId);
    }

    public ModelProvider findProvider(Long providerId) {
        return modelProviderMapper.selectById(providerId);
    }

    /**
     * 业务调用入口：模型、供应商必须启用，且必需的密钥必须已配置。
     */
    public ModelRuntime resolveForExecution(Long configId) {
        ModelConfig config = requireConfig(configId);
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE,
                    "模型「" + config.getDisplayName() + "」已停用");
        }
        ModelProvider provider = requireProvider(config.getProviderId());
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE,
                    "模型供应商「" + provider.getProviderName() + "」已停用");
        }
        return buildRuntime(config, provider);
    }

    /**
     * 连通测试允许对已停用的模型发起，但仍要求密钥完整并可解密。
     */
    public ModelRuntime resolveForTest(Long configId, long timeoutSeconds) {
        ModelConfig config = requireConfig(configId);
        ModelProvider provider = requireProvider(config.getProviderId());
        return buildRuntime(config, provider).withTimeoutSeconds(timeoutSeconds);
    }

    public boolean hasApiKey(ModelConfig config) {
        return StringUtils.hasText(config.getApiKeyCipher());
    }

    public boolean isAvailable(ModelConfig config, ModelProvider provider) {
        return unavailableReason(config, provider) == null;
    }

    public String unavailableReason(ModelConfig config, ModelProvider provider) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return "模型已停用";
        }
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            return "供应商已停用";
        }
        if (Boolean.TRUE.equals(provider.getRequiresApiKey()) && !hasApiKey(config)) {
            return "尚未配置 API Key";
        }
        if (!StringUtils.hasText(resolveBaseUrl(config, provider))) {
            return "缺少 API Base URL";
        }
        return null;
    }

    public List<String> parseCapabilities(String capabilitiesJson) {
        if (!StringUtils.hasText(capabilitiesJson)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(capabilitiesJson, new TypeReference<>() {
            });
            return values.stream().filter(StringUtils::hasText).map(v -> v.trim().toUpperCase(Locale.ROOT)).toList();
        } catch (Exception ex) {
            log.warn("Invalid capabilities JSON in model config: {}", ex.getMessage());
            return List.of();
        }
    }

    public String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public String resolveBaseUrl(ModelConfig config, ModelProvider provider) {
        String value = StringUtils.hasText(config.getApiBaseUrl())
                ? config.getApiBaseUrl()
                : provider.getDefaultBaseUrl();
        return normalizeBaseUrl(value);
    }

    private ModelRuntime buildRuntime(ModelConfig config, ModelProvider provider) {
        String baseUrl = resolveBaseUrl(config, provider);
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型缺少 API Base URL");
        }
        validateBaseUrl(baseUrl);

        boolean requiresApiKey = Boolean.TRUE.equals(provider.getRequiresApiKey());
        String apiKey = null;
        if (requiresApiKey) {
            if (!hasApiKey(config)) {
                throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE,
                        "模型「" + config.getDisplayName() + "」尚未配置 API Key，无法发起真实调用");
            }
            try {
                apiKey = secretCryptoService.decrypt(config.getApiKeyCipher());
            } catch (IllegalStateException ex) {
                throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE,
                        "模型「" + config.getDisplayName() + "」的 API Key 无法解密，请重新保存密钥");
            }
        } else if (hasApiKey(config)) {
            try {
                apiKey = secretCryptoService.decrypt(config.getApiKeyCipher());
            } catch (IllegalStateException ex) {
                apiKey = "not-required";
            }
        } else {
            apiKey = "not-required";
        }

        ProviderProtocol protocol;
        try {
            protocol = ProviderProtocol.valueOf(provider.getProtocol().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE,
                    "不支持的供应商协议：" + provider.getProtocol());
        }

        ModelType modelType;
        try {
            modelType = ModelType.valueOf(config.getModelType().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE,
                    "不支持的模型类型：" + config.getModelType());
        }

        List<String> capabilities = parseCapabilities(config.getCapabilities());
        if (capabilities.isEmpty()) {
            capabilities = List.of(modelType.name());
        }

        long timeout = modelType == ModelType.EMBEDDING ? EMBEDDING_TIMEOUT_SECONDS : CHAT_TIMEOUT_SECONDS;
        BigDecimal temperature = config.getTemperature() == null ? BigDecimal.valueOf(0.7) : config.getTemperature();

        return new ModelRuntime(
                config.getId(),
                provider.getId(),
                provider.getProviderCode(),
                config.getDisplayName(),
                protocol,
                baseUrl,
                apiKey,
                config.getModelName(),
                modelType,
                temperature.doubleValue(),
                config.getMaxTokens(),
                config.getContextWindow(),
                config.getEmbeddingDimension(),
                capabilities,
                config.getInputPricePer1k(),
                config.getOutputPricePer1k(),
                config.getCurrency(),
                timeout
        );
    }

    private ModelConfig requireConfig(Long configId) {
        ModelConfig config = modelConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型配置不存在或已删除");
        }
        return config;
    }

    private ModelProvider requireProvider(Long providerId) {
        ModelProvider provider = modelProviderMapper.selectById(providerId);
        if (provider == null) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE, "模型供应商不存在");
        }
        return provider;
    }

    private void validateBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("unsupported url");
            }
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.MODEL_UNAVAILABLE,
                    "API Base URL 不合法，仅支持 http/https");
        }
    }
}
