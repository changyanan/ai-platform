package com.aiplatform.integration.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabeledAddressExtractorTest {

    private final LabeledAddressExtractor extractor = new LabeledAddressExtractor();

    @Test
    void parsesInternationalFormLabels() {
        String ocrText = """
                订单号:         CEDP990321333534CN        KID:
                买家姓名         Cheryll Ann Gibson
                地址1          46 Daisy Dell Road,
                地址2
                城市           BOROREN
                州/省          QLD
                国家          Australia
                电话           61238138871-3170
                邮箱           nweozk5jjt12074@gs.shipping.ter
                邮编           4678
                门牌
                """;

        AddressJsonParser.ParsedAddress address = extractor.parse(ocrText);

        assertThat(address).isNotNull();
        assertThat(address.addressType()).isEqualTo("INTERNATIONAL");
        assertThat(address.orderNo()).isEqualTo("CEDP990321333534CN");
        assertThat(address.receiverName()).isEqualTo("Cheryll Ann Gibson");
        assertThat(address.addressLine1()).isEqualTo("46 Daisy Dell Road");
        assertThat(address.city()).isEqualTo("BOROREN");
        assertThat(address.state()).isEqualTo("QLD");
        assertThat(address.country()).isEqualTo("Australia");
        assertThat(address.phone()).isEqualTo("61238138871-3170");
        assertThat(address.email()).isEqualTo("nweozk5jjt12074@gs.shipping.ter");
        assertThat(address.postalCode()).isEqualTo("4678");
        assertThat(address.fullAddress())
                .isEqualTo("46 Daisy Dell Road, BOROREN, QLD, 4678, Australia");
    }

    @Test
    void recoversNameAndEmailWhenLabelsAreGarbled() {
        String ocrText = """
                订单号:         CEDP990321333534CN        ID:
                RRIF         Cheryll Ann Gibson
                地址1          46 Daisy Dell Road,
                地址2
                城市           BOROREN
                州/省          QLD
                国家          Australia
                电话           61238138871-3170
                邮箱           nweozk5jjt12074@gs.shipping.ter
                邮编           4678
                门牌
                """;

        AddressJsonParser.ParsedAddress address = extractor.parse(ocrText);

        assertThat(address).isNotNull();
        assertThat(address.receiverName()).isEqualTo("Cheryll Ann Gibson");
        assertThat(address.email()).isEqualTo("nweozk5jjt12074@gs.shipping.ter");
        assertThat(address.phone()).isEqualTo("61238138871-3170");
    }

    @Test
    void parsesWrappedGermanShippingAddressBlock() {
        String ocrText = """
                收货地址                                            发货地                                              运输方式
                Goran Hair Beauty friseursalon, Von-Bru 。”DE @@                                          快递
                g-Straße 7, 82467, Garmisch-Partenkirc                                                     Shipped via supplier Method
                hen, Bayern, DE
                """;

        AddressJsonParser.ParsedAddress address = extractor.parse(ocrText);

        assertThat(address).isNotNull();
        assertThat(address.addressType()).isEqualTo("INTERNATIONAL");
        assertThat(address.addressLine1())
                .isEqualTo("Goran Hair Beauty friseursalon, Von-Brug-Straße 7");
        assertThat(address.city()).isEqualTo("Garmisch-Partenkirchen");
        assertThat(address.state()).isEqualTo("Bayern");
        assertThat(address.postalCode()).isEqualTo("82467");
        assertThat(address.country()).isEqualTo("Germany");
        assertThat(address.fullAddress()).isEqualTo(
                "Goran Hair Beauty friseursalon, Von-Brug-Straße 7, "
                        + "Garmisch-Partenkirchen, Bayern, 82467, Germany");
    }

    @Test
    void parsesTemuOrderDetailsWithAddressBlock() {
        String ocrText = """
                Order details order ip-po-210-05319847921272882
                Refund order
                Recipient name
                Anthony Davis
                Phone number
                +44 7904 157930
                Shipping address @
                3 Terry Drive
                Aylesbury, (Buckinghamshire), HP19 9NA
                United Kingdom
                Order contents
                Virtual email
                va4ed3jai71301e@eul.shipping.temuemail.com
                """;

        AddressJsonParser.ParsedAddress address = extractor.parse(ocrText);

        assertThat(address).isNotNull();
        assertThat(address.orderNo()).isEqualTo("PO-210-05319847921272882");
        assertThat(address.receiverName()).isEqualTo("Anthony Davis");
        assertThat(address.phone()).isEqualTo("+44 7904 157930");
        assertThat(address.email()).isEqualTo("va4ed3jai71301e@eul.shipping.temuemail.com");
        assertThat(address.country()).isEqualTo("United Kingdom");
        assertThat(address.state()).isEqualTo("Buckinghamshire");
        assertThat(address.city()).isEqualTo("Aylesbury");
        assertThat(address.postalCode()).isEqualTo("HP19 9NA");
        assertThat(address.addressLine1()).isEqualTo("3 Terry Drive");
        assertThat(address.fullAddress()).isEqualTo(
                "3 Terry Drive, Aylesbury, Buckinghamshire, HP19 9NA, United Kingdom");
    }

    @Test
    void parsesColumnarTemuPageWithNoisyLabels() {
        String ocrText = """
                Order details omer ip-po-210-05319847921272882
                Refund order
                Recipient name                                      Phone number
                Shipped in time®                                    7d 16h
                Anthony Davis                                       +44 7904 1579300
                Purchase date                    Shipping service                    Shipping address @ Virtual email
                3 Terry Drive
                vad4ed3jai71301e@eul.shipping.temuemail.com
                Aylesbury, (Buckinghamshire), HP19 9NA
                United Kingdom
                Order contents
                """;

        AddressJsonParser.ParsedAddress address = extractor.parse(ocrText);

        assertThat(address).isNotNull();
        assertThat(address.orderNo()).isEqualTo("PO-210-05319847921272882");
        assertThat(address.receiverName()).isEqualTo("Anthony Davis");
        assertThat(address.phone()).isEqualTo("+44 7904 1579300");
        assertThat(address.email()).isEqualTo("vad4ed3jai71301e@eul.shipping.temuemail.com");
        assertThat(address.country()).isEqualTo("United Kingdom");
        assertThat(address.state()).isEqualTo("Buckinghamshire");
        assertThat(address.city()).isEqualTo("Aylesbury");
        assertThat(address.postalCode()).isEqualTo("HP19 9NA");
        assertThat(address.addressLine1()).isEqualTo("3 Terry Drive");
    }

    @Test
    void parsesLooseInternationalFromMixedColumns() {
        String ocrText = """
                Order details omer ip-po-210-05319847921272882
                Recipient name                                      Phone number
                Anthony Davis                                       +44 7904 157930
                Purchase date                    Shipping service                    Shipping address @ Virtual email
                Nov 3, 2026, 3:43 pm CET(UTC+1)  Standard  3 Terry Drive  vad4ed3jai71301e@eul.shipping.temuemail.com
                Aylesbury, (Buckinghamshire), HP19 9NA   Estimated revenue £33.77
                United Kingdom
                """;

        AddressJsonParser.ParsedAddress address = extractor.parse(ocrText);

        assertThat(address).isNotNull();
        assertThat(address.receiverName()).isEqualTo("Anthony Davis");
        assertThat(address.addressLine1()).isEqualTo("3 Terry Drive");
        assertThat(address.city()).isEqualTo("Aylesbury");
        assertThat(address.state()).isEqualTo("Buckinghamshire");
        assertThat(address.postalCode()).isEqualTo("HP19 9NA");
        assertThat(address.country()).isEqualTo("United Kingdom");
    }

    @Test
    void returnsNullForUnstructuredText() {
        assertThat(extractor.parse("帮我查一下这个订单什么时候发货")).isNull();
    }
}
