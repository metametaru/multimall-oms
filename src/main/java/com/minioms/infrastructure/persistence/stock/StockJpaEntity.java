package com.minioms.infrastructure.persistence.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

/**
 * stocks テーブルのJPAマッピング。ドメインの {@code Stock} とは別物であり、
 * 相互変換は {@link StockMapper} が担う。
 */
@Entity
@Table(name = "stocks")
class StockJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "quantity_allocated", nullable = false)
    private int quantityAllocated;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StockJpaEntity() {
        // JPA用
    }

    StockJpaEntity(Long id, String productCode, int quantityOnHand, int quantityAllocated, Long version) {
        this.id = id;
        this.productCode = productCode;
        this.quantityOnHand = quantityOnHand;
        this.quantityAllocated = quantityAllocated;
        this.version = version;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    /** Why not: 数量を個別に設定させない。実在庫と引当済は必ず対で整合する値として書き換える */
    void changeQuantities(int quantityOnHand, int quantityAllocated) {
        this.quantityOnHand = quantityOnHand;
        this.quantityAllocated = quantityAllocated;
    }

    Long getId() {
        return id;
    }

    String getProductCode() {
        return productCode;
    }

    int getQuantityOnHand() {
        return quantityOnHand;
    }

    int getQuantityAllocated() {
        return quantityAllocated;
    }

    Long getVersion() {
        return version;
    }
}
