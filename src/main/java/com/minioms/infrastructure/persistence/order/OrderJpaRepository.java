package com.minioms.infrastructure.persistence.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    // 明細まで一括で取る。open-in-view を無効にしているため、
    // トランザクション外で遅延ロードすると確実に壊れる
    @EntityGraph(attributePaths = "items")
    Optional<OrderJpaEntity> findByMallIdAndMallOrderNumber(Long mallId, String mallOrderNumber);
}
