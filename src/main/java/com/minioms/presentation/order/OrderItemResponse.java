package com.minioms.presentation.order;

import com.minioms.domain.order.OrderItem;

import java.math.BigDecimal;

record OrderItemResponse(
        Long id,
        String productCode,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal) {

    static OrderItemResponse from(OrderItem item) {
        // 小計はドメインの計算結果をそのまま返す。表示側で再計算すると計算ルールが二重になる
        return new OrderItemResponse(
                item.id(), item.productCode(), item.productName(),
                item.unitPrice(), item.quantity(), item.subtotal());
    }
}
