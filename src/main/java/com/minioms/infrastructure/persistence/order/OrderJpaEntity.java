package com.minioms.infrastructure.persistence.order;

import com.minioms.domain.order.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * orders テーブルのJPAマッピング。ドメインの {@code Order} とは別物であり、
 * 相互変換は {@link OrderMapper} が担う。
 *
 * <p>Why not: malls への {@code @ManyToOne} 関連は張らない。受注処理でモールの
 * 属性を参照する場面がなく、関連を張ると受注取得のたびに不要なJOINや遅延ロードを
 * 招くため、FK値(mall_id)をそのまま保持する。</p>
 */
@Entity
@Table(name = "orders")
class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mall_id", nullable = false)
    private Long mallId;

    @Column(name = "mall_order_number", nullable = false, length = 64)
    private String mallOrderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal totalAmount;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // Why not: 双方向関連にはしない。明細から受注を辿る業務要件がなく、
    // 双方向にすると両側の整合を保つ定型コードが増えるため
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    protected OrderJpaEntity() {
        // JPA用
    }

    OrderJpaEntity(Long id, Long mallId, String mallOrderNumber, OrderStatus status, String customerName,
                   BigDecimal totalAmount, OffsetDateTime orderedAt, List<OrderItemJpaEntity> items, Long version) {
        this.id = id;
        this.mallId = mallId;
        this.mallOrderNumber = mallOrderNumber;
        this.status = status;
        this.customerName = customerName;
        this.totalAmount = totalAmount;
        this.orderedAt = orderedAt;
        this.items = new ArrayList<>(items);
        this.version = version;
    }

    // Why not: imported_at / updated_at はDB DEFAULT に任せない。
    // DEFAULT はINSERT時にしか効かず、UPDATE時の updated_at が更新されないため
    @PrePersist
    void onInsert() {
        OffsetDateTime now = OffsetDateTime.now();
        this.importedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    void changeStatus(OrderStatus status) {
        this.status = status;
    }

    Long getId() {
        return id;
    }

    Long getMallId() {
        return mallId;
    }

    String getMallOrderNumber() {
        return mallOrderNumber;
    }

    OrderStatus getStatus() {
        return status;
    }

    String getCustomerName() {
        return customerName;
    }

    BigDecimal getTotalAmount() {
        return totalAmount;
    }

    OffsetDateTime getOrderedAt() {
        return orderedAt;
    }

    List<OrderItemJpaEntity> getItems() {
        return items;
    }

    Long getVersion() {
        return version;
    }
}
