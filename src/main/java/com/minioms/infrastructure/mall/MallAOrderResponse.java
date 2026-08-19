package com.minioms.infrastructure.mall;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * モールA(JSON形式)のレスポンス。
 * 日時はISO-8601、金額は数値、明細はネストした配列という素直な形式。
 */
record MallAOrderResponse(List<MallAOrder> orders) {

    record MallAOrder(String orderNo,
                      String customerName,
                      OffsetDateTime orderedAt,
                      BigDecimal totalAmount,
                      List<MallAItem> items) {
    }

    record MallAItem(String sku, String name, BigDecimal price, int qty) {
    }
}
