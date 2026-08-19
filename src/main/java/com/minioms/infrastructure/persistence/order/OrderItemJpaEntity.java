package com.minioms.infrastructure.persistence.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * order_items テーブルのJPAマッピング。
 * order_id は親側の {@code @JoinColumn} が管理するためここには持たせない。
 */
@Entity
@Table(name = "order_items")
class OrderItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected OrderItemJpaEntity() {
        // JPA用
    }

    OrderItemJpaEntity(Long id, String productCode, String productName, BigDecimal unitPrice, int quantity) {
        this.id = id;
        this.productCode = productCode;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    Long getId() {
        return id;
    }

    String getProductCode() {
        return productCode;
    }

    String getProductName() {
        return productName;
    }

    BigDecimal getUnitPrice() {
        return unitPrice;
    }

    int getQuantity() {
        return quantity;
    }
}
