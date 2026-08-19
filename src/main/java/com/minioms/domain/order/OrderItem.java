package com.minioms.domain.order;

import java.math.BigDecimal;

/**
 * 受注明細。受注集約に属する値オブジェクト。
 *
 * @param id 永続化前は null(採番はDBに委ねる)
 */
public record OrderItem(Long id, String productCode, String productName, BigDecimal unitPrice, int quantity) {

    public OrderItem {
        if (productCode == null || productCode.isBlank()) {
            throw new IllegalArgumentException("商品コードは必須です");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("商品名は必須です");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("単価が不正です: " + unitPrice);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量は1以上である必要があります: " + quantity);
        }
    }

    /** 新規取込時に使う。IDはまだ存在しない */
    public static OrderItem of(String productCode, String productName, BigDecimal unitPrice, int quantity) {
        return new OrderItem(null, productCode, productName, unitPrice, quantity);
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
