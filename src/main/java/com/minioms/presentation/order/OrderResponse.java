package com.minioms.presentation.order;

import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 受注詳細のレスポンス。
 *
 * @param version 楽観ロック用。更新系APIにそのまま送り返してもらう
 */
record OrderResponse(
        long id,
        String mallCode,
        String mallOrderNumber,
        OrderStatus status,
        String statusLabel,
        String customerName,
        BigDecimal totalAmount,
        BigDecimal itemsSubtotal,
        OffsetDateTime orderedAt,
        List<OrderItemResponse> items,
        long version,
        List<OrderActionResponse> availableActions) {

    static OrderResponse from(Order order, String mallCode) {
        return new OrderResponse(
                order.id(),
                mallCode,
                order.mallOrderKey().mallOrderNumber(),
                order.status(),
                OrderStatusLabel.of(order.status()),
                order.customerName(),
                order.totalAmount(),
                // モールの提示額と一致しないことがある(送料・モール側割引)。
                // 差異に気づけるよう両方返す
                order.itemsSubtotal(),
                order.orderedAt(),
                order.items().stream().map(OrderItemResponse::from).toList(),
                order.version(),
                OrderAction.availableFrom(order.status()));
    }
}
