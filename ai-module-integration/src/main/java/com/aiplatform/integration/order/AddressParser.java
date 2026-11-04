package com.aiplatform.integration.order;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文收货地址确定性拆分器。
 * 大模型负责理解语义；本类负责在模型只给出完整地址时补齐省/市/区/街道，
 * 并对模型给出的拆分结果做一致性校验，避免地址字段全部堆在 detailAddress。
 */
public final class AddressParser {

    private static final Pattern MUNICIPALITY = Pattern.compile("^(北京市|上海市|天津市|重庆市)");
    private static final Pattern PROVINCE = Pattern.compile("^(.+?(?:省|自治区|特别行政区))");
    private static final Pattern CITY = Pattern.compile("^(.+?(?:市|自治州|地区|盟))");
    private static final Pattern DISTRICT = Pattern.compile("^(.+?(?:区|县|旗|市))");
    private static final Pattern STREET = Pattern.compile("^(.+?(?:街道|镇|乡))");
    private static final Pattern STREET_WITH_HOUSE_NUMBER =
            Pattern.compile("^(.*?(?:街道|大道|路|巷|弄|街|道))([0-9０-９].*)$");
    private static final Pattern ROAD_WITH_HOUSE_NUMBER =
            Pattern.compile("^(.*?(?:大道|路|巷|弄|街|道))([0-9０-９].*)$");
    private static final Pattern ADDRESS_START = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,12}?(?:省|自治区|特别行政区)|北京市|上海市|天津市|重庆市|"
                    + "[\\u4e00-\\u9fa5]{2,12}?(?:市|自治州|地区|盟))");

    private AddressParser() {
    }

    public static AddressParts parse(String fullAddress) {
        if (!StringUtils.hasText(fullAddress)) {
            return AddressParts.empty();
        }
        String remaining = fullAddress.trim().replaceAll("\\s+", " ");
        String province = null;
        String city = null;
        String district = null;
        String street = null;

        Match match = match(MUNICIPALITY, remaining);
        if (match != null) {
            province = match.value();
            city = match.value();
            remaining = match.remaining();
        } else {
            match = match(PROVINCE, remaining);
            if (match != null) {
                province = match.value();
                remaining = match.remaining();
            }
            match = match(CITY, remaining);
            if (match != null) {
                city = match.value();
                remaining = match.remaining();
            }
        }

        match = match(DISTRICT, remaining);
        if (match != null) {
            district = match.value();
            remaining = match.remaining();
        }

        match = match(STREET, remaining);
        if (match != null) {
            street = match.value();
            remaining = match.remaining();
        }

        return new AddressParts(province, city, district, street,
                StringUtils.hasText(remaining) ? remaining.trim() : null,
                fullAddress.trim());
    }

    /**
     * 从一段包含姓名、电话、订单信息的自然语言中定位地址片段。
     * 只做定位和确定性拆分，不猜测姓名/电话。
     */
    public static AddressParts parseFromText(String text) {
        if (!StringUtils.hasText(text)) {
            return AddressParts.empty();
        }
        Matcher matcher = ADDRESS_START.matcher(text);
        if (!matcher.find()) {
            return AddressParts.empty();
        }
        String candidate = text.substring(matcher.start()).trim();
        int cut = candidate.length();
        for (String delimiter : new String[]{"，", ",", "。", "；", ";", "\n", "\r",
                "电话", "手机", "订单", "商品", "数量", "金额", "备注"}) {
            int index = candidate.indexOf(delimiter);
            if (index >= 0 && index < cut) {
                cut = index;
            }
        }
        candidate = candidate.substring(0, cut).trim();
        return parse(candidate);
    }

    /**
     * 把模型常见的“文三路90号 / 5楼”整理为街道“文三路”、详细地址“90号5楼”。
     * 该方法是纯确定性的，不改变完整地址。
     */
    public static AddressParts refine(AddressParts parts) {
        if (parts == null) {
            return AddressParts.empty();
        }
        String street = parts.street();
        String detail = parts.detailAddress();
        if (StringUtils.hasText(street)) {
            Matcher matcher = STREET_WITH_HOUSE_NUMBER.matcher(street.trim());
            if (matcher.matches()) {
                street = matcher.group(1).trim();
                String houseNumber = matcher.group(2).trim();
                if (!StringUtils.hasText(detail)) {
                    detail = houseNumber;
                } else if (!detail.startsWith(houseNumber)) {
                    detail = houseNumber + detail;
                }
            }
        } else if (StringUtils.hasText(detail)) {
            Matcher matcher = ROAD_WITH_HOUSE_NUMBER.matcher(detail.trim());
            if (matcher.matches()) {
                street = matcher.group(1).trim();
                detail = matcher.group(2).trim();
            }
        }
        return new AddressParts(parts.province(), parts.city(), parts.district(),
                street, detail, parts.fullAddress());
    }

    /**
     * 模型结果优先；仅补齐空字段，不覆盖模型已识别的内容。
     */
    public static AddressParts fillMissing(String fullAddress, AddressParts current) {
        AddressParts base = current == null ? AddressParts.empty() : current;
        AddressParts parsed = parse(StringUtils.hasText(fullAddress) ? fullAddress : base.fullAddress());
        String province = firstText(base.province(), parsed.province());
        String city = firstText(base.city(), parsed.city());
        String district = firstText(base.district(), parsed.district());
        String street = firstText(base.street(), parsed.street());
        String detail = firstText(base.detailAddress(), parsed.detailAddress());
        String full = firstText(base.fullAddress(),
                StringUtils.hasText(fullAddress) ? fullAddress.trim()
                        : join(province, city, district, street, detail));
        return new AddressParts(province, city, district, street, detail, full);
    }

    public static String join(String province, String city, String district, String street, String detail) {
        StringBuilder builder = new StringBuilder();
        append(builder, province);
        if (StringUtils.hasText(city) && !city.equals(province)) {
            append(builder, city);
        }
        append(builder, district);
        append(builder, street);
        append(builder, detail);
        return builder.isEmpty() ? null : builder.toString();
    }

    private static void append(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(value.trim());
        }
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : (StringUtils.hasText(second) ? second.trim() : null);
    }

    private static Match match(Pattern pattern, String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.lookingAt()) {
            return null;
        }
        String value = matcher.group(1).trim();
        if (value.isEmpty()) {
            return null;
        }
        return new Match(value, text.substring(matcher.end()).trim());
    }

    private record Match(String value, String remaining) {
    }

    public record AddressParts(
            String province,
            String city,
            String district,
            String street,
            String detailAddress,
            String fullAddress
    ) {
        public static AddressParts empty() {
            return new AddressParts(null, null, null, null, null, null);
        }
    }
}
