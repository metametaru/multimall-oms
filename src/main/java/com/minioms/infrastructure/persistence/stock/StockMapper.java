package com.minioms.infrastructure.persistence.stock;

import com.minioms.domain.stock.Stock;

/** ドメインモデルとJPAエンティティの相互変換 */
final class StockMapper {

    private StockMapper() {
    }

    static StockJpaEntity toEntity(Stock stock) {
        return new StockJpaEntity(
                stock.id(),
                stock.productCode(),
                stock.quantityOnHand(),
                stock.quantityAllocated(),
                stock.id() == null ? null : stock.version());
    }

    static Stock toDomain(StockJpaEntity entity) {
        return new Stock(
                entity.getId(),
                entity.getProductCode(),
                entity.getQuantityOnHand(),
                entity.getQuantityAllocated(),
                entity.getVersion());
    }
}
