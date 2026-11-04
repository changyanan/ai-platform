package com.aiplatform.model.runtime;

import com.aiplatform.model.enums.ModelCapability;
import com.aiplatform.model.enums.ModelType;
import com.aiplatform.model.enums.ProviderProtocol;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一次模型调用的不可变运行参数。
 * 注意：toString 已显式隐藏 apiKey，禁止把该对象整体序列化到日志或 API 响应。
 */
public record ModelRuntime(
        Long configId,
        Long providerId,
        String providerCode,
        String displayName,
        ProviderProtocol protocol,
        String baseUrl,
        String apiKey,
        String modelName,
        ModelType modelType,
        double temperature,
        Integer maxTokens,
        Integer contextWindow,
        Integer embeddingDimension,
        List<String> capabilities,
        BigDecimal inputPricePerThousand,
        BigDecimal outputPricePerThousand,
        String currency,
        long timeoutSeconds
) {

    public boolean supports(ModelCapability capability) {
        return capabilities != null && capabilities.contains(capability.name());
    }

    public ModelRuntime withTimeoutSeconds(long timeout) {
        return new ModelRuntime(configId, providerId, providerCode, displayName, protocol, baseUrl, apiKey,
                modelName, modelType, temperature, maxTokens, contextWindow, embeddingDimension, capabilities,
                inputPricePerThousand, outputPricePerThousand, currency, timeout);
    }

    public ModelRuntime withMaxTokensAndTemperature(Integer tokens, double newTemperature) {
        return new ModelRuntime(configId, providerId, providerCode, displayName, protocol, baseUrl, apiKey,
                modelName, modelType, newTemperature, tokens, contextWindow, embeddingDimension, capabilities,
                inputPricePerThousand, outputPricePerThousand, currency, timeoutSeconds);
    }

    @Override
    public String toString() {
        return "ModelRuntime[" +
                "configId=" + configId +
                ", providerCode='" + providerCode + '\'' +
                ", modelName='" + modelName + '\'' +
                ", modelType=" + modelType +
                ", baseUrl='" + baseUrl + '\'' +
                ", apiKey=***]";
    }
}
