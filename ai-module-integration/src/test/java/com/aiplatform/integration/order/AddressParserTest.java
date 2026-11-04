package com.aiplatform.integration.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressParserTest {

    @Test
    void splitsProvinceCityDistrictAndDetail() {
        AddressParser.AddressParts parts = AddressParser.parse("浙江省杭州市西湖区文三路90号5楼");

        assertThat(parts.province()).isEqualTo("浙江省");
        assertThat(parts.city()).isEqualTo("杭州市");
        assertThat(parts.district()).isEqualTo("西湖区");
        assertThat(parts.street()).isNull();
        assertThat(parts.detailAddress()).isEqualTo("文三路90号5楼");
        assertThat(parts.fullAddress()).isEqualTo("浙江省杭州市西湖区文三路90号5楼");
    }

    @Test
    void splitsMunicipalityWithoutDuplicatingCity() {
        AddressParser.AddressParts parts = AddressParser.parse("北京市朝阳区建国路88号");

        assertThat(parts.province()).isEqualTo("北京市");
        assertThat(parts.city()).isEqualTo("北京市");
        assertThat(parts.district()).isEqualTo("朝阳区");
        assertThat(parts.detailAddress()).isEqualTo("建国路88号");
    }

    @Test
    void splitsStreetAndDetail() {
        AddressParser.AddressParts parts = AddressParser.parse(
                "广东省深圳市南山区粤海街道科技园路1号");

        assertThat(parts.province()).isEqualTo("广东省");
        assertThat(parts.city()).isEqualTo("深圳市");
        assertThat(parts.district()).isEqualTo("南山区");
        assertThat(parts.street()).isEqualTo("粤海街道");
        assertThat(parts.detailAddress()).isEqualTo("科技园路1号");
    }

    @Test
    void splitsCityOnlyAddress() {
        AddressParser.AddressParts parts = AddressParser.parse("杭州市西湖区文一路100号");

        assertThat(parts.province()).isNull();
        assertThat(parts.city()).isEqualTo("杭州市");
        assertThat(parts.district()).isEqualTo("西湖区");
        assertThat(parts.detailAddress()).isEqualTo("文一路100号");
    }

    @Test
    void fillsMissingFieldsFromFullAddress() {
        AddressParser.AddressParts current = new AddressParser.AddressParts(
                null, null, null, null, "5楼", null);
        AddressParser.AddressParts filled = AddressParser.fillMissing(
                "浙江省杭州市西湖区文三路90号", current);

        assertThat(filled.province()).isEqualTo("浙江省");
        assertThat(filled.city()).isEqualTo("杭州市");
        assertThat(filled.district()).isEqualTo("西湖区");
        assertThat(filled.detailAddress()).isEqualTo("5楼");
        assertThat(filled.fullAddress()).isEqualTo("浙江省杭州市西湖区文三路90号");
    }

    @Test
    void findsAddressInsideOrderText() {
        AddressParser.AddressParts parts = AddressParser.parseFromText(
                "张三 13800001234 浙江省杭州市西湖区文三路90号5楼，打印机2台，货到付款");

        assertThat(parts.province()).isEqualTo("浙江省");
        assertThat(parts.city()).isEqualTo("杭州市");
        assertThat(parts.district()).isEqualTo("西湖区");
        assertThat(parts.fullAddress()).isEqualTo("浙江省杭州市西湖区文三路90号5楼");
    }

    @Test
    void movesRoadAndHouseNumberOutOfDetail() {
        AddressParser.AddressParts refined = AddressParser.refine(new AddressParser.AddressParts(
                "浙江省", "杭州市", "西湖区", null, "文三路90号5楼",
                "浙江省杭州市西湖区文三路90号5楼"));

        assertThat(refined.street()).isEqualTo("文三路");
        assertThat(refined.detailAddress()).isEqualTo("90号5楼");
    }

    @Test
    void joinsMissingFullAddressFromParts() {
        AddressParser.AddressParts parts = AddressParser.fillMissing(null,
                new AddressParser.AddressParts("浙江省", "杭州市", "西湖区", null, "文三路90号", null));

        assertThat(parts.fullAddress()).isEqualTo("浙江省杭州市西湖区文三路90号");
    }
}
