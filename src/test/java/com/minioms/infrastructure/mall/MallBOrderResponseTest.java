package com.minioms.infrastructure.mall;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * モールBのXMLレスポンスの読み取り仕様。
 * HTTPを介さずパース単体を固定することで、通信の失敗と形式の解釈ミスを切り分ける。
 */
@DisplayName("モールBのXMLパース")
class MallBOrderResponseTest {

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <orderList>
              <order>
                <no>B0000123</no>
                <buyer>佐藤花子</buyer>
                <orderDatetime>20260819103000</orderDatetime>
                <amount>3200</amount>
                <statusCode>01</statusCode>
                <detailList>
                  <detail>
                    <itemCd>SKU-777</itemCd>
                    <itemNm>モバイルバッテリー</itemNm>
                    <price>1600</price>
                    <cnt>2</cnt>
                  </detail>
                </detailList>
              </order>
              <order>
                <no>B0000124</no>
                <buyer>田中次郎</buyer>
                <orderDatetime>20260819120000</orderDatetime>
                <amount>980</amount>
                <statusCode>03</statusCode>
                <detailList>
                  <detail>
                    <itemCd>SKU-888</itemCd>
                    <itemNm>スマホスタンド</itemNm>
                    <price>980</price>
                    <cnt>1</cnt>
                  </detail>
                </detailList>
              </order>
            </orderList>
            """;

    @Test
    void 繰り返す注文要素をリストとして読む() throws Exception {
        MallBOrderResponse response = new XmlMapper().readValue(XML, MallBOrderResponse.class);

        assertThat(response.orders()).hasSize(2);
        assertThat(response.orders().get(0).no()).isEqualTo("B0000123");
        assertThat(response.orders().get(0).buyer()).isEqualTo("佐藤花子");
        assertThat(response.orders().get(0).statusCode()).isEqualTo("01");
        assertThat(response.orders().get(0).amount()).isEqualByComparingTo("3200");
    }

    @Test
    void 明細はdetailListでラップされた繰り返し要素として読む() throws Exception {
        MallBOrderResponse response = new XmlMapper().readValue(XML, MallBOrderResponse.class);

        assertThat(response.orders().get(0).details())
                .singleElement()
                .satisfies(detail -> {
                    assertThat(detail.itemCd()).isEqualTo("SKU-777");
                    assertThat(detail.itemNm()).isEqualTo("モバイルバッテリー");
                    assertThat(detail.cnt()).isEqualTo(2);
                });
    }
}
