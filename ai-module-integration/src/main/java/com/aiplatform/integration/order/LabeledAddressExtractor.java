package com.aiplatform.integration.order;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 带标签表单/订单详情的确定性解析器。
 *
 * <p>OCR 对“订单号 / Recipient name / Shipping address / 城市 / 州省 / 国家 /
 * 电话 / 邮箱 / 邮编”这类页面的识别结果通常带有稳定标签。先按标签解析，
 * 可以避免小参数模型把电话和邮箱混淆、把英文地址强行套用中国省市区结构。</p>
 */
@Component
public class LabeledAddressExtractor {

    private static final String LABELS = String.join("|",
            "收货地址", "Shipping\\s*Address", "Ship\\s*to\\s*Address",
            "地址1", "Address\\s*Line\\s*1", "Address\\s*1",
            "地址2", "Address\\s*Line\\s*2", "Address\\s*2",
            "地址",
            "订单号", "Order\\s*No\\.?", "Order\\s*Number", "Order\\s*ID",
            "Order\\s*Id", "Order\\s*IP", "Order\\s*Details",
            "买家ID", "买家\\s*ID", "Buyer\\s*ID", "KID",
            "买家姓名", "收货人", "Recipient\\s*Name", "Buyer\\s*Name", "Receiver", "Name",
            "城市", "City",
            "州/省", "州省", "State", "Province",
            "国家", "Country",
            "电话", "手机", "Phone\\s*Number", "Phone", "Tel",
            "邮箱", "Virtual\\s*Email", "Email", "E-mail",
            "邮编", "Postal\\s*Code", "Zip\\s*Code", "Zip",
            "门牌", "Door\\s*Number", "House\\s*Number",
            "发货地", "Ship\\s*From", "运输方式", "Shipping\\s*Method"
    );

    private static final Pattern LABEL_LINE = Pattern.compile(
            "^\\s*(" + LABELS + ")\\s*[:：]?\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ORDER_NO = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9\\-]{4,}");
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile(
            "(?i)(?:order|omer)\\s*(?:id|ip)?\\s*[:：-]\\s*([A-Za-z0-9-]{6,})");
    private static final Pattern SHIPPING_BLOCK_PATTERN = Pattern.compile(
            "(?is)Shipping\\s*Address[^\\n]*\\n(.+?)"
                    + "(?=\\n\\s*(?:Virtual\\s*Email|Order\\s*Contents|Order\\s*Summary|$))");
    private static final Pattern STREET_ADDRESS_PATTERN = Pattern.compile(
            "(?i)(\\d+[A-Za-z]?\\s+[A-Z][A-Za-z .'-]{2,50}?"
                    + "(?:Drive|Road|Street|Avenue|Lane|Way|Court|Place|Boulevard|Terrace|Close))");
    private static final Pattern UK_POSTCODE_PATTERN = Pattern.compile(
            "(?i)\\b[A-Z]{1,2}\\d[A-Z\\d]?\\s*\\d[A-Z]{2}\\b");
    private static final Pattern CITY_STATE_PATTERN = Pattern.compile(
            "([A-Z][A-Za-z .'-]{2,40}),\\s*\\(([^)]{2,40})\\)");
    private static final List<String> COUNTRY_NAMES_LIST = List.of(
            "United Kingdom", "Germany", "Australia", "United States",
            "France", "Canada", "Japan", "China");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\+?\\d[\\d\\s\\-()]{6,}\\d");
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z .'-]{2,60}$");
    private static final Pattern POSTAL_UK = Pattern.compile(
            "(?i)^[A-Z]{1,2}\\d[A-Z\\d]?\\s*\\d[A-Z]{2}$");
    private static final Pattern POSTAL_DE = Pattern.compile("^\\d{5}$");
    private static final Pattern POSTAL_US = Pattern.compile("^\\d{5}(-\\d{4})?$");

    private static final Map<String, String> COUNTRY_NAMES = Map.of(
            "DE", "Germany",
            "GB", "United Kingdom",
            "UK", "United Kingdom",
            "US", "United States",
            "FR", "France",
            "AU", "Australia",
            "CA", "Canada",
            "JP", "Japan",
            "CN", "China"
    );

    public AddressJsonParser.ParsedAddress parse(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        String fallbackName = null;
        boolean orderSeen = false;
        boolean address1Seen = false;
        String[] lines = text.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (!orderSeen && line.matches("(?i).*(订单号|order\\s*(id|ip|details)).*")) {
                orderSeen = true;
            }
            Matcher matcher = LABEL_LINE.matcher(line);
            if (!matcher.matches()) {
                if (orderSeen && !address1Seen && fallbackName == null) {
                    String candidate = nameCandidate(line);
                    if (candidate != null) {
                        fallbackName = candidate;
                    }
                }
                continue;
            }

            String key = normalizeLabel(matcher.group(1));
            String value = cleanValue(matcher.group(2));
            if ("section".equals(key)) {
                continue;
            }
            if ("addressBlock".equals(key)) {
                StringBuilder block = new StringBuilder(truncateAtSection(value));
                int j = i + 1;
                while (j < lines.length) {
                    String next = lines[j].trim();
                    if (!StringUtils.hasText(next)) {
                        j++;
                        continue;
                    }
                    if (looksLikeLabel(next)) {
                        break;
                    }
                    if (EMAIL_PATTERN.matcher(next).find()) {
                        j++;
                        continue;
                    }
                    String fragment = truncateAtSection(next);
                    if (!StringUtils.hasText(fragment)) {
                        if (isBlockStop(next)) {
                            break;
                        }
                        j++;
                        continue;
                    }
                    block.append('\n').append(fragment);
                    j++;
                }
                String collected = block.toString().trim();
                if (StringUtils.hasText(collected)) {
                    values.putIfAbsent("addressBlock", collected);
                }
                address1Seen = true;
                i = j - 1;
                continue;
            }
            if ("orderNo".equals(key)) {
                orderSeen = true;
            }
            if ("addressLine1".equals(key)) {
                address1Seen = true;
            }
            if (!StringUtils.hasText(value) && ("receiverName".equals(key)
                    || "phone".equals(key) || "email".equals(key))) {
                int j = i + 1;
                while (j < lines.length && !StringUtils.hasText(lines[j].trim())) {
                    j++;
                }
                if (j < lines.length && !LABEL_LINE.matcher(lines[j].trim()).matches()) {
                    value = cleanValue(lines[j].trim());
                    i = j;
                }
            }
            if (StringUtils.hasText(value)) {
                values.putIfAbsent(key, value);
            }
        }

        if (values.isEmpty() && fallbackName == null) {
            return null;
        }

        AddressJsonParser.ParsedAddress block = parseAddressBlock(values.get("addressBlock"));
        if (block == null) {
            block = parseAddressBlock(shippingBlockFromText(text));
        }
        if (!StringUtils.hasText(values.get("addressLine1"))) {
            AddressJsonParser.ParsedAddress loose = parseLooseInternational(text);
            if (loose != null && (block == null || blockScore(loose) > blockScore(block))) {
                block = loose;
            }
        }
        String orderNo = firstText(orderNo(values.get("orderNo")), orderNoFromText(text));
        String buyerId = values.get("buyerId");
        String receiverName = firstText(values.get("receiverName"), fallbackName);
        if (!isValidName(receiverName)) {
            receiverName = recipientFromText(text);
        }
        String phone = values.get("phone");
        if (!isValidPhone(phone)) {
            phone = phoneFromText(text);
        }
        String email = firstText(values.get("email"), findPattern(EMAIL_PATTERN, text, false));
        String country = values.get("country");
        String state = values.get("state");
        String city = values.get("city");
        String addressLine1 = values.get("addressLine1");
        String addressLine2 = values.get("addressLine2");
        String postalCode = values.get("postalCode");
        String doorNumber = values.get("doorNumber");
        String province = null;
        String district = null;
        String street = null;
        String detailAddress = null;
        String fullAddress = null;
        String blockType = null;

        if (block != null) {
            country = firstText(country, block.country());
            state = firstText(state, block.state());
            city = firstText(city, block.city());
            addressLine1 = firstText(addressLine1, block.addressLine1());
            addressLine2 = firstText(addressLine2, block.addressLine2());
            postalCode = firstText(postalCode, block.postalCode());
            province = block.province();
            district = block.district();
            street = block.street();
            detailAddress = block.detailAddress();
            fullAddress = block.fullAddress();
            blockType = block.addressType();
            if (!StringUtils.hasText(receiverName)) {
                receiverName = block.receiverName();
            }
        }

        if (!StringUtils.hasText(addressLine1) && !StringUtils.hasText(receiverName)
                && !StringUtils.hasText(phone) && !StringUtils.hasText(email)
                && !StringUtils.hasText(country) && !StringUtils.hasText(orderNo)) {
            return null;
        }

        boolean international = "INTERNATIONAL".equals(blockType)
                || (StringUtils.hasText(country)
                && !"中国".equals(country) && !"china".equalsIgnoreCase(country))
                || (StringUtils.hasText(addressLine1) && !StringUtils.hasText(province));
        String addressType = international ? "INTERNATIONAL" : "DOMESTIC";
        if (!StringUtils.hasText(detailAddress)) {
            detailAddress = addressLine1;
        }
        if (international) {
            String composed = join(addressLine1, addressLine2, city, state, postalCode, country);
            if (StringUtils.hasText(composed)) {
                fullAddress = composed;
            }
        }
        if (!StringUtils.hasText(fullAddress)) {
            fullAddress = join(addressLine1, addressLine2, city, state, postalCode, country);
        }

        return new AddressJsonParser.ParsedAddress(
                addressType,
                orderNo,
                buyerId,
                receiverName,
                phone,
                email,
                country,
                state,
                province,
                city,
                district,
                street,
                addressLine1,
                addressLine2,
                detailAddress,
                postalCode,
                doorNumber,
                fullAddress,
                null
        );
    }

    private AddressJsonParser.ParsedAddress parseAddressBlock(String block) {
        if (!StringUtils.hasText(block)) {
            return null;
        }
        String cleaned = block.replaceAll("[@®]+$", "")
                .replace("StraBe", "Straße")
                .trim();
        String joined = joinWrappedLines(cleaned);
        if (!StringUtils.hasText(joined)) {
            return null;
        }
        if (joined.matches(".*[\\u4e00-\\u9fa5].*")) {
            AddressParser.AddressParts parts = AddressParser.parseFromText(joined);
            if (StringUtils.hasText(parts.province()) || StringUtils.hasText(parts.city())
                    || StringUtils.hasText(parts.detailAddress())) {
                return new AddressJsonParser.ParsedAddress(
                        "DOMESTIC", null, null, null, null, null, "China", null,
                        parts.province(), parts.city(), parts.district(), parts.street(),
                        null, null, parts.detailAddress(), null, null, parts.fullAddress(), null);
            }
        }
        return parseInternationalBlock(joined);
    }

    private String joinWrappedLines(String block) {
        List<String> lines = new ArrayList<>();
        for (String raw : block.split("\\r?\\n")) {
            if (EMAIL_PATTERN.matcher(raw).find()) {
                continue;
            }
            String line = raw.replaceAll("[@®]", "")
                    .replaceAll("[。”]", "")
                    .trim();
            if (StringUtils.hasText(line)) {
                lines.add(line);
            }
        }
        StringBuilder builder = new StringBuilder();
        String previous = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i < lines.size() - 1) {
                // 去掉混入地址行的国家代码（下一行末尾才是真正的国家）
                line = line.replaceAll("\\s+[A-Z]{2}\\s*$", "").trim();
            }
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (builder.isEmpty()) {
                builder.append(line);
            } else if (previous != null && previous.matches(".*[A-Za-z]$")
                    && line.matches("^[a-z].*")) {
                // OCR 把单词从中间换行：Von-Bru + g-Straße、Partenkirc + hen
                builder.append(line);
            } else {
                builder.append(", ").append(line);
            }
            previous = line;
        }
        return builder.toString();
    }

    private AddressJsonParser.ParsedAddress parseInternationalBlock(String joined) {
        String[] rawParts = joined.split("\\s*,\\s*");
        List<String> parts = new ArrayList<>();
        for (String part : rawParts) {
            String value = part.replaceAll("[@®]+$", "").trim();
            if (StringUtils.hasText(value)) {
                parts.add(value);
            }
        }
        if (parts.size() < 2) {
            return null;
        }

        String country = null;
        String countryCode = null;
        String last = parts.get(parts.size() - 1);
        if (last.matches("^[A-Za-z]{2}$")) {
            countryCode = last;
            country = COUNTRY_NAMES.getOrDefault(last.toUpperCase(Locale.ROOT), last);
            parts.remove(parts.size() - 1);
        } else if (last.matches("(?i).*(kingdom|germany|states|france|australia|canada|japan|china).*")) {
            country = last;
            parts.remove(parts.size() - 1);
        }
        if (country != null) {
            String finalCountryCode = countryCode;
            String finalCountry = country;
            parts.removeIf(part -> part.equalsIgnoreCase(finalCountry)
                    || (finalCountryCode != null && part.equalsIgnoreCase(finalCountryCode)));
        }

        String postalCode = null;
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            if (POSTAL_UK.matcher(part).matches() || POSTAL_DE.matcher(part).matches()
                    || POSTAL_US.matcher(part).matches()) {
                postalCode = part;
                parts.remove(i);
                break;
            }
        }

        String state = null;
        for (int i = 0; i < parts.size(); i++) {
            Matcher matcher = Pattern.compile("^\\((.*)\\)$").matcher(parts.get(i));
            if (matcher.matches()) {
                state = matcher.group(1).trim();
                parts.remove(i);
                break;
            }
        }
        if (state == null && postalCode != null && parts.size() >= 2) {
            state = parts.remove(parts.size() - 1);
        }

        String city = parts.size() >= 2 ? parts.remove(parts.size() - 1) : null;
        String addressLine1 = String.join(", ", parts);
        if (!StringUtils.hasText(addressLine1)) {
            return null;
        }
        String fullAddress = join(addressLine1, null, city, state, postalCode, country);
        return new AddressJsonParser.ParsedAddress(
                "INTERNATIONAL", null, null, null, null, null, country, state,
                null, city, null, null, addressLine1, null, addressLine1,
                postalCode, null, fullAddress, null);
    }

    private AddressJsonParser.ParsedAddress parseLooseInternational(String text) {
        Matcher streetMatcher = STREET_ADDRESS_PATTERN.matcher(text);
        if (!streetMatcher.find()) {
            return null;
        }
        String addressLine1 = streetMatcher.group(1).trim();
        String postalCode = null;
        Matcher postalMatcher = UK_POSTCODE_PATTERN.matcher(text);
        if (postalMatcher.find()) {
            postalCode = postalMatcher.group().toUpperCase(Locale.ROOT);
        }
        String city = null;
        String state = null;
        Matcher cityStateMatcher = CITY_STATE_PATTERN.matcher(text);
        if (cityStateMatcher.find()) {
            city = cityStateMatcher.group(1).trim();
            state = cityStateMatcher.group(2).trim();
        }
        String country = null;
        for (String candidate : COUNTRY_NAMES_LIST) {
            if (text.toLowerCase(Locale.ROOT).contains(candidate.toLowerCase(Locale.ROOT))) {
                country = candidate;
                break;
            }
        }
        if (country == null && postalCode != null) {
            country = "United Kingdom";
        }
        if (country == null) {
            return null;
        }
        String fullAddress = join(addressLine1, null, city, state, postalCode, country);
        return new AddressJsonParser.ParsedAddress(
                "INTERNATIONAL", null, null, null, null, null, country, state,
                null, city, null, null, addressLine1, null, addressLine1,
                postalCode, null, fullAddress, null);
    }

    private int blockScore(AddressJsonParser.ParsedAddress address) {
        if (address == null) {
            return -1;
        }
        int score = 0;
        if (StringUtils.hasText(address.addressLine1())) {
            score += address.addressLine1().matches(".*\\d.*") ? 3 : 1;
        }
        score += StringUtils.hasText(address.city()) ? 1 : 0;
        score += StringUtils.hasText(address.state()) ? 1 : 0;
        score += StringUtils.hasText(address.postalCode()) ? 1 : 0;
        score += StringUtils.hasText(address.country()) ? 1 : 0;
        return score;
    }

    private String truncateAtSection(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String result = value;
        for (String marker : new String[]{"发货地", "运输方式", "国际贸易", "Ship from",
                "Shipping method", "快递", "Shipped via", "Shipped Via", "shipped via",
                "Method", "Virtual email", "Virtual Email", "Virtual emai",
                "Order contents", "Order Contents", "Order summary", "Order Summary"}) {
            int index = result.indexOf(marker);
            if (index >= 0) {
                result = result.substring(0, index);
            }
        }
        return result.trim();
    }

    private boolean isBlockStop(String line) {
        String normalized = line.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return normalized.contains("发货地") || normalized.contains("运输方式")
                || normalized.contains("国际贸易") || normalized.contains("shippedvia")
                || normalized.contains("快递") || normalized.contains("method")
                || normalized.contains("virtualemail") || normalized.contains("virtualemai")
                || normalized.contains("ordercontents") || normalized.contains("ordersummary");
    }

    private boolean looksLikeLabel(String line) {
        if (LABEL_LINE.matcher(line).matches()) {
            return true;
        }
        String normalized = line.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return normalized.contains("virtualemail") || normalized.contains("virtualemai")
                || normalized.contains("recipientname") || normalized.contains("phonenumber")
                || normalized.contains("shippingaddress") || normalized.contains("发货地")
                || normalized.contains("运输方式") || normalized.contains("orderdetails");
    }

    private String normalizeLabel(String label) {
        String value = label.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (value.contains("收货地址") || value.contains("shippingaddress")
                || value.contains("shiptoaddress") || "地址".equals(value)) {
            return "addressBlock";
        }
        if (value.contains("发货地") || value.contains("shipfrom")
                || value.contains("运输方式") || value.contains("shippingmethod")
                || value.contains("orderdetails")) {
            return "section";
        }
        if (value.contains("订单") || value.startsWith("order")) {
            return "orderNo";
        }
        if (value.contains("买家id") || value.contains("buyerid") || "kid".equals(value)) {
            return "buyerId";
        }
        if (value.contains("姓名") || value.contains("收货人") || value.contains("name")
                || value.contains("receiver") || value.contains("recipient")) {
            return "receiverName";
        }
        if (value.contains("地址1") || value.contains("addressline1") || value.contains("address1")) {
            return "addressLine1";
        }
        if (value.contains("地址2") || value.contains("addressline2") || value.contains("address2")) {
            return "addressLine2";
        }
        if (value.contains("城市") || value.contains("city")) {
            return "city";
        }
        if (value.contains("州") || value.contains("省") || value.contains("state")
                || value.contains("province")) {
            return "state";
        }
        if (value.contains("国家") || value.contains("country")) {
            return "country";
        }
        if (value.contains("电话") || value.contains("手机") || value.contains("phone")
                || value.contains("tel")) {
            return "phone";
        }
        if (value.contains("邮箱") || value.contains("email") || value.contains("mail")) {
            return "email";
        }
        if (value.contains("邮编") || value.contains("postal") || value.contains("zip")) {
            return "postalCode";
        }
        if (value.contains("门牌") || value.contains("door") || value.contains("house")) {
            return "doorNumber";
        }
        return value;
    }

    private String orderNo(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = ORDER_NO.matcher(value.trim());
        return matcher.find() ? matcher.group() : null;
    }

    private String shippingBlockFromText(String text) {
        Matcher matcher = SHIPPING_BLOCK_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String recipientFromText(String text) {
        String[] lines = text.split("\\r?\\n");
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].matches("(?i).*recipient\\s*name.*")) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        for (int i = start + 1; i < Math.min(lines.length, start + 7); i++) {
            String[] segments = lines[i].trim().split("\\s{2,}");
            for (String segment : segments) {
                String candidate = segment.trim();
                if (isValidName(candidate) && NAME_PATTERN.matcher(candidate).matches()
                        && candidate.contains(" ")) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String phoneFromText(String text) {
        String[] lines = text.split("\\r?\\n");
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].matches("(?i).*phone\\s*number.*")) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        for (int i = start; i < Math.min(lines.length, start + 7); i++) {
            Matcher matcher = PHONE_PATTERN.matcher(lines[i]);
            while (matcher.find()) {
                String candidate = matcher.group().trim();
                if (isValidPhone(candidate) && candidate.replaceAll("\\D", "").length() >= 9) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean isValidName(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return !value.matches("(?i).*(refund|order|shipping|shipped|deliver|delivery|"
                + "address|phone|email|summary|contents|seller|buyer|support|messages|"
                + "details|purchase|standard|payment|total|price|date).*");
    }

    private boolean isValidPhone(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.length() >= 7 && digits.length() <= 15;
    }

    private String orderNoFromText(String text) {
        Matcher matcher = ORDER_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private String cleanValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.trim()
                .replaceAll("[,，;；:：@®]+$", "")
                .trim();
        if (!StringUtils.hasText(cleaned) || "null".equalsIgnoreCase(cleaned)) {
            return null;
        }
        return cleaned;
    }

    private String nameCandidate(String line) {
        String candidate = line;
        String[] segments = line.split("\\s{2,}");
        if (segments.length > 1) {
            candidate = segments[segments.length - 1].trim();
        }
        if (candidate.matches(".*\\d.*")) {
            return null;
        }
        return NAME_PATTERN.matcher(candidate).matches() && candidate.contains(" ")
                ? candidate : null;
    }

    private String findPattern(Pattern pattern, String text, boolean longest) {
        Matcher matcher = pattern.matcher(text);
        String best = null;
        while (matcher.find()) {
            String value = matcher.group().trim();
            if (!longest || best == null || value.length() > best.length()) {
                best = value;
            }
        }
        return best;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String join(String... values) {
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
}
