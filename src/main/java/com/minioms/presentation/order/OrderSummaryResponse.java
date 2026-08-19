package com.minioms.presentation.order;

import com.minioms.application.order.OrderSummary;
import com.minioms.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 受注一覧の1行。
 *
 * <p>Why not: 明細は含めない。一覧で全件分の明細を返すと、画面が使わない
 * データのために応答が肥大する。明細が要るのは詳細画面だけ。</p>
 */
record OrderSummaryResponse(
        long id,
        String mallCode,
        String mallOrderNumber,
        OrderStatus status,
        String customerName,
        BigDecimal totalAmount,
        OffsetDateTime orderedAt,
        long version) {

    static OrderSummaryResponse from(OrderSummary summary, String mallCode) {
        return new OrderSummaryResponse(
                summary.id(), mallCode, summary.mallOrderNumber(), summary.status(),
                summary.customerName(), summary.totalAmount(), summary.orderedAt(), summary.version());
    }
}
