package com.minioms.infrastructure.mall.mock;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部ECモールAPIのモック。実在モールの仕様は模倣せず、
 * 「形式が違う2つのモール」という抽象化だけを表現する。
 *
 * <p>Why not: レスポンスをオブジェクトから生成せず文字列で持つ。外部システムが
 * 返す生の形式をそのまま目視できる方が、腐敗防止層のテストデータとして読みやすい。</p>
 *
 * <p>Why not: {@code since} / {@code from} での絞り込みは実装しない。取込側が
 * 毎回同じ受注を受け取ることで、2回目以降の実行が冪等キーでスキップされる様子を
 * そのまま観察できるようにしている。</p>
 */
@RestController
@RequestMapping("/mock")
@Profile("mock")
class MockMallController {

    /** モールA: JSON形式。日時はISO-8601、素直な構造 */
    @GetMapping(value = "/mall-a/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    String mallAOrders(@RequestParam(required = false) String since) {
        return """
                {
                  "orders": [
                    {
                      "orderNo": "A-20260819-0001",
                      "customerName": "山田太郎",
                      "orderedAt": "2026-08-19T10:00:00+09:00",
                      "totalAmount": 5400,
                      "items": [
                        { "sku": "SKU-001", "name": "ワイヤレスイヤホン", "price": 2500, "qty": 2 }
                      ]
                    },
                    {
                      "orderNo": "A-20260819-0002",
                      "customerName": "鈴木一郎",
                      "orderedAt": "2026-08-19T11:30:00+09:00",
                      "totalAmount": 1200,
                      "items": [
                        { "sku": "SKU-002", "name": "USB-Cケーブル", "price": 600, "qty": 2 }
                      ]
                    }
                  ]
                }
                """;
    }

    /** モールB: XML形式。要素名も日時形式もステータス表現もモールAと異なる */
    @GetMapping(value = "/mall-b/orderList", produces = MediaType.APPLICATION_XML_VALUE)
    String mallBOrderList(@RequestParam(required = false) String from) {
        return """
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
                  <order>
                    <no>B0000125</no>
                    <buyer>高橋三郎</buyer>
                    <orderDatetime>20260819131500</orderDatetime>
                    <amount>4500</amount>
                    <statusCode>77</statusCode>
                    <detailList>
                      <detail>
                        <itemCd>SKU-999</itemCd>
                        <itemNm>ノートPCスタンド</itemNm>
                        <price>4500</price>
                        <cnt>1</cnt>
                      </detail>
                    </detailList>
                  </order>
                </orderList>
                """;
    }
}
