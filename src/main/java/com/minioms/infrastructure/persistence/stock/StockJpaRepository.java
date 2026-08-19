package com.minioms.infrastructure.persistence.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface StockJpaRepository extends JpaRepository<StockJpaEntity, Long> {

    Optional<StockJpaEntity> findByProductCode(String productCode);

    /**
     * 行ロックを取って在庫を取得する(SELECT ... FOR UPDATE)。
     *
     * <p>引当は「読んで、計算して、書く」操作であり、読みと書きの間に別の処理が
     * 割り込むと在庫を二重に引き当ててしまう。ロックで直列化して防ぐ。</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockJpaEntity s WHERE s.productCode = :productCode")
    Optional<StockJpaEntity> findByProductCodeForUpdate(@Param("productCode") String productCode);
}
