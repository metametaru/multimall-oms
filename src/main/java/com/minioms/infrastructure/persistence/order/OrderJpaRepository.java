package com.minioms.infrastructure.persistence.order;

import com.minioms.application.order.OrderSummary;
import com.minioms.domain.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    // 明細まで一括で取る。open-in-view を無効にしているため、
    // トランザクション外で遅延ロードすると確実に壊れる
    @EntityGraph(attributePaths = "items")
    Optional<OrderJpaEntity> findByMallIdAndMallOrderNumber(Long mallId, String mallOrderNumber);

    // Why not: 継承した findById は使わない。EntityGraph が効かず、
    // 明細が遅延ロードのまま呼び出し側に渡ってしまうため
    @EntityGraph(attributePaths = "items")
    Optional<OrderJpaEntity> findWithItemsById(Long id);

    /**
     * 一覧用の検索。明細を読まずに受注単位の列だけを取り出す。
     *
     * <p>Why not: エンティティを取得してから変換する形にしない。一覧は明細を使わないため、
     * 集約を復元すると表示しない明細の取得が件数分走る。</p>
     *
     * <p>Why not: 並び順を呼び出し側の {@code Pageable} に委ねない。
     * 「未確認の受注を古い順に処理する」という運用が仕様であり、
     * インデックス {@code idx_orders_status_ordered_at} もこの順序を前提にしている。</p>
     */
    @Query(value = """
            SELECT new com.minioms.application.order.OrderSummary(
                o.id, o.mallId, o.mallOrderNumber, o.status, o.customerName,
                o.totalAmount, o.orderedAt, o.version)
            FROM OrderJpaEntity o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:mallId IS NULL OR o.mallId = :mallId)
            ORDER BY o.orderedAt ASC
            """,
            countQuery = """
            SELECT count(o) FROM OrderJpaEntity o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:mallId IS NULL OR o.mallId = :mallId)
            """)
    Page<OrderSummary> search(@Param("status") OrderStatus status,
                              @Param("mallId") Long mallId,
                              Pageable pageable);
}
