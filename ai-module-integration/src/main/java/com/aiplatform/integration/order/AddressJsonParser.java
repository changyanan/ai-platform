package com.aiplatform.integration.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 地址抽取结果解析。只接受 JSON，不把模型输出直接暴露给页面；
 * 地址字段会经过确定性拆分器补齐，避免只返回一整段地址。
 */
@Component
@RequiredArgsConstructor
public class AddressJsonParser {

    private final ObjectMapper objectMapper;

    public ParsedAddress parse(String rawOutput) {
        JsonNode root = readJson(rawOutput);
        ParsedAddress address = normalize(root);
        if (!hasMeaningfulData(address)) {
            throw new IllegalArgumentException("模型未识别到收货人、电话或地址信息");
        }
        return address;
    }

    public ParsedAddress normalize(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("模型结果必须是 JSON 对象");
        }
        String receiverName = text(input.get("receiverName"));
        if (receiverName == null) {
            receiverName = text(input.get("customerName"));
        }
        String phone = normalizePhone(text(input.get("phone")));
        String email = text(input.get("email"));
        String orderNo = text(input.get("orderNo"));
        String buyerId = text(input.get("buyerId"));
        String country = text(input.get("country"));
        String state = text(input.get("state"));
        String addressLine1 = text(input.get("addressLine1"));
        String addressLine2 = text(input.get("addressLine2"));
        String postalCode = text(input.get("postalCode"));
        String doorNumber = text(input.get("doorNumber"));
        String fullAddress = text(input.get("fullAddress"));

        String addressType = text(input.get("addressType"));
        boolean international = isInternationalCountry(country)
                || isInternational(country, addressLine1, fullAddress);
        if (international) {
            // 模型可能把国外地址误标为 DOMESTIC；国家字段优先级更高。
            addressType = "INTERNATIONAL";
        } else if (addressType == null) {
            addressType = "DOMESTIC";
        }
        addressType = addressType.toUpperCase(java.util.Locale.ROOT);
        if (!"INTERNATIONAL".equals(addressType)) {
            addressType = "DOMESTIC";
        }

        if (StringUtils.hasText(orderNo) && (orderNo.contains("@")
                || orderNo.equalsIgnoreCase(email))) {
            orderNo = null;
        }
        if (StringUtils.hasText(buyerId) && (buyerId.contains("@")
                || buyerId.equalsIgnoreCase(email))) {
            buyerId = null;
        }

        String province = text(input.get("province"));
        String city = text(input.get("city"));
        String district = text(input.get("district"));
        String street = text(input.get("street"));
        String detailAddress = text(input.get("detailAddress"));

        if ("INTERNATIONAL".equals(addressType)) {
            if (StringUtils.hasText(addressLine2) && addressLine2.equalsIgnoreCase(city)) {
                addressLine2 = null;
            }
            String composed = joinInternational(addressLine1, addressLine2, city, state,
                    postalCode, country);
            if (StringUtils.hasText(composed)) {
                fullAddress = composed;
            }
            detailAddress = addressLine1;
        } else {
            AddressParser.AddressParts current = new AddressParser.AddressParts(
                    province, city, district, street, detailAddress, fullAddress);
            AddressParser.AddressParts filled = AddressParser.fillMissing(
                    current.fullAddress(), current);
            filled = AddressParser.refine(filled);
            province = filled.province();
            city = filled.city();
            district = filled.district();
            street = filled.street();
            detailAddress = filled.detailAddress();
            fullAddress = filled.fullAddress();
        }

        BigDecimal confidence = decimal(input.get("confidence"));
        if (confidence != null) {
            confidence = confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE)
                    .setScale(4, RoundingMode.HALF_UP);
        }
        return new ParsedAddress(addressType, orderNo, buyerId, receiverName, phone, email,
                country, state, province, city, district, street,
                addressLine1, addressLine2, detailAddress, postalCode, doorNumber,
                fullAddress, confidence);
    }

    public boolean hasMeaningfulData(ParsedAddress address) {
        return address != null && (StringUtils.hasText(address.receiverName())
                || StringUtils.hasText(address.phone())
                || StringUtils.hasText(address.email())
                || StringUtils.hasText(address.orderNo())
                || StringUtils.hasText(address.fullAddress())
                || StringUtils.hasText(address.country())
                || StringUtils.hasText(address.addressLine1())
                || StringUtils.hasText(address.province())
                || StringUtils.hasText(address.city())
                || StringUtils.hasText(address.district())
                || StringUtils.hasText(address.detailAddress()));
    }

    private boolean isInternationalCountry(String country) {
        return StringUtils.hasText(country)
                && !"中国".equalsIgnoreCase(country.trim())
                && !"china".equalsIgnoreCase(country.trim());
    }

    private boolean isInternational(String country, String addressLine1, String fullAddress) {
        if (isInternationalCountry(country)) {
            return true;
        }
        String value = StringUtils.hasText(addressLine1) ? addressLine1 : fullAddress;
        return StringUtils.hasText(value) && value.matches(".*[A-Za-z]{3,}.*");
    }

    private String joinInternational(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                if (!builder.isEmpty()) {
                    builder.append(", ");
                }
                builder.append(value.trim());
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private JsonNode readJson(String rawOutput) {
        if (!StringUtils.hasText(rawOutput)) {
            throw new IllegalArgumentException("模型未返回任何内容");
        }
        String text = rawOutput.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                text = text.substring(firstLine + 1, lastFence).trim();
            }
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignored) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readTree(text.substring(start, end + 1));
                } catch (Exception ex) {
                    throw new IllegalArgumentException("模型输出包含非法 JSON");
                }
            }
            throw new IllegalArgumentException("模型未返回合法 JSON 对象");
        }
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.isTextual() ? node.asText() : node.toString();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        String compact = trimmed.replaceAll("[\\s\\-()]", "");
        if (compact.matches("\\+?861[3-9]\\d{9}")) {
            return compact.replaceFirst("^\\+?86", "");
        }
        if (compact.matches("1[3-9]\\d{9}")) {
            return compact;
        }
        // 国外号码保留原始分隔符，避免把 +1 (555) 010-9999 改成不可读数字串。
        return trimmed;
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String value = node.asText();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        value = value.replaceAll("[^0-9.\\-]", "");
        if (!StringUtils.hasText(value) || "-".equals(value) || ".".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record ParsedAddress(
            String addressType,
            String orderNo,
            String buyerId,
            String receiverName,
            String phone,
            String email,
            String country,
            String state,
            String province,
            String city,
            String district,
            String street,
            String addressLine1,
            String addressLine2,
            String detailAddress,
            String postalCode,
            String doorNumber,
            String fullAddress,
            BigDecimal confidence
    ) {
    }
}
