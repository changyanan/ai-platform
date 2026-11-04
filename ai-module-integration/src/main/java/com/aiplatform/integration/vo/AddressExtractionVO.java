package com.aiplatform.integration.vo;

import com.aiplatform.integration.order.AddressJsonParser;

import java.math.BigDecimal;

/**
 * 地址识别结果。该对象只用于当前请求的页面回填，不落库。
 */
public record AddressExtractionVO(
        String receiverName,
        String phone,
        String province,
        String city,
        String district,
        String street,
        String detailAddress,
        String fullAddress,
        BigDecimal confidence,
        String sourceType,
        String addressType,
        String orderNo,
        String buyerId,
        String email,
        String country,
        String state,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String doorNumber,
        Long modelConfigId,
        String modelName,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        boolean tokenEstimated,
        boolean ocrUsed,
        Long latencyMs
) {

    public static AddressExtractionVO from(AddressJsonParser.ParsedAddress address,
                                           String sourceType, Long modelConfigId, String modelName,
                                           Integer promptTokens, Integer completionTokens,
                                           boolean tokenEstimated, boolean ocrUsed, long latencyMs) {
        Integer total = promptTokens == null || completionTokens == null
                ? null : promptTokens + completionTokens;
        return new AddressExtractionVO(
                address.receiverName(),
                address.phone(),
                address.province(),
                address.city(),
                address.district(),
                address.street(),
                address.detailAddress(),
                address.fullAddress(),
                address.confidence(),
                sourceType,
                address.addressType(),
                address.orderNo(),
                address.buyerId(),
                address.email(),
                address.country(),
                address.state(),
                address.addressLine1(),
                address.addressLine2(),
                address.postalCode(),
                address.doorNumber(),
                modelConfigId,
                modelName,
                promptTokens,
                completionTokens,
                total,
                tokenEstimated,
                ocrUsed,
                latencyMs
        );
    }
}
