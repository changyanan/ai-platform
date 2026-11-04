package com.aiplatform.model.vo;

import com.aiplatform.model.entity.ModelProvider;

public record ModelProviderVO(
        Long id,
        String providerCode,
        String providerName,
        String protocol,
        String defaultBaseUrl,
        boolean requiresApiKey,
        String description,
        boolean enabled
) {

    public static ModelProviderVO from(ModelProvider provider) {
        return new ModelProviderVO(
                provider.getId(),
                provider.getProviderCode(),
                provider.getProviderName(),
                provider.getProtocol(),
                provider.getDefaultBaseUrl(),
                Boolean.TRUE.equals(provider.getRequiresApiKey()),
                provider.getDescription(),
                Boolean.TRUE.equals(provider.getEnabled())
        );
    }
}
