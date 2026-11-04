package com.aiplatform.integration.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressJsonParserTest {

    private final AddressJsonParser parser = new AddressJsonParser(new ObjectMapper());

    @Test
    void parsesFencedJsonAndNormalizesPhone() {
        String raw = """
                ```json
                {
                  "receiverName": "张三",
                  "phone": "+86 138-0000-1234",
                  "province": "浙江省",
                  "city": "杭州市",
                  "district": "西湖区",
                  "street": "文三路",
                  "detailAddress": "90号5楼",
                  "fullAddress": "浙江省杭州市西湖区文三路90号5楼",
                  "confidence": 0.93
                }
                ```
                """;

        AddressJsonParser.ParsedAddress address = parser.parse(raw);

        assertThat(address.receiverName()).isEqualTo("张三");
        assertThat(address.phone()).isEqualTo("13800001234");
        assertThat(address.province()).isEqualTo("浙江省");
        assertThat(address.city()).isEqualTo("杭州市");
        assertThat(address.district()).isEqualTo("西湖区");
        assertThat(address.street()).isEqualTo("文三路");
        assertThat(address.detailAddress()).isEqualTo("90号5楼");
        assertThat(address.confidence()).isEqualByComparingTo("0.9300");
    }

    @Test
    void movesHouseNumberFromStreetIntoDetail() {
        AddressJsonParser.ParsedAddress address = parser.parse("""
                {"receiverName":"王五","phone":"13700002222",
                 "province":"浙江省","city":"杭州市","district":"西湖区",
                 "street":"文三路90号","detailAddress":"5楼",
                 "fullAddress":"浙江省杭州市西湖区文三路90号5楼"}
                """);

        assertThat(address.street()).isEqualTo("文三路");
        assertThat(address.detailAddress()).isEqualTo("90号5楼");
        assertThat(address.fullAddress()).isEqualTo("浙江省杭州市西湖区文三路90号5楼");
    }

    @Test
    void fillsAddressPartsWhenModelOnlyReturnsFullAddress() {
        AddressJsonParser.ParsedAddress address = parser.parse("""
                {"receiverName":"李四","phone":"13900001111",
                 "fullAddress":"广东省深圳市南山区粤海街道科技园路1号","confidence":0.8}
                """);

        assertThat(address.province()).isEqualTo("广东省");
        assertThat(address.city()).isEqualTo("深圳市");
        assertThat(address.district()).isEqualTo("南山区");
        assertThat(address.street()).isEqualTo("粤海街道");
        assertThat(address.detailAddress()).isEqualTo("科技园路1号");
    }

    @Test
    void parsesInternationalAddressWithoutChineseProvinceSplit() {
        AddressJsonParser.ParsedAddress address = parser.parse("""
                {
                  "addressType":"INTERNATIONAL",
                  "orderNo":"CEDP990321333534CN",
                  "receiverName":"Cheryll Ann Gibson",
                  "phone":"61238138871-3170",
                  "email":"buyer@example.com",
                  "country":"Australia",
                  "state":"QLD",
                  "city":"BOROREN",
                  "addressLine1":"46 Daisy Dell Road",
                  "addressLine2":"Unit 2",
                  "postalCode":"4678",
                  "fullAddress":"46 Daisy Dell Road, Unit 2, BOROREN, QLD, 4678, Australia",
                  "confidence":0.9
                }
                """);

        assertThat(address.addressType()).isEqualTo("INTERNATIONAL");
        assertThat(address.orderNo()).isEqualTo("CEDP990321333534CN");
        assertThat(address.country()).isEqualTo("Australia");
        assertThat(address.state()).isEqualTo("QLD");
        assertThat(address.addressLine1()).isEqualTo("46 Daisy Dell Road");
        assertThat(address.addressLine2()).isEqualTo("Unit 2");
        assertThat(address.postalCode()).isEqualTo("4678");
        assertThat(address.phone()).isEqualTo("61238138871-3170");
        assertThat(address.province()).isNull();
        assertThat(address.district()).isNull();
    }

    @Test
    void overridesWrongDomesticTypeWhenCountryIsForeign() {
        AddressJsonParser.ParsedAddress address = parser.parse("""
                {
                  "addressType":"DOMESTIC",
                  "orderNo":"buyer@example.com",
                  "receiverName":"Cheryll Ann Gibson",
                  "email":"buyer@example.com",
                  "country":"Australia",
                  "state":"QLD",
                  "city":"BOROREN",
                  "addressLine1":"46 Daisy Dell Road",
                  "addressLine2":"BOROREN",
                  "postalCode":"4678"
                }
                """);

        assertThat(address.addressType()).isEqualTo("INTERNATIONAL");
        assertThat(address.orderNo()).isNull();
        assertThat(address.addressLine2()).isNull();
        assertThat(address.fullAddress())
                .isEqualTo("46 Daisy Dell Road, BOROREN, QLD, 4678, Australia");
    }

    @Test
    void rejectsNonJsonOutput() {
        assertThatThrownBy(() -> parser.parse("我无法识别这张图片"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void rejectsEmptyAddressResult() {
        assertThatThrownBy(() -> parser.parse("""
                {"receiverName":null,"phone":null,"province":null,"city":null,
                 "district":null,"street":null,"detailAddress":null,"fullAddress":null}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未识别");
    }
}
