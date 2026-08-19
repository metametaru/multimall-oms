package com.minioms.infrastructure.persistence.stock;

import com.minioms.application.stock.StockRepository;
import com.minioms.domain.stock.DuplicateStockException;
import com.minioms.domain.stock.Stock;
import com.minioms.domain.stock.StockNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * {@link StockRepository} のJPA実装。
 */
@Repository
@RequiredArgsConstructor
class StockRepositoryAdapter implements StockRepository {

    private final StockJpaRepository jpaRepository;

    @Override
    @Transactional
    public Stock save(Stock stock) {
        return stock.id() == null ? insert(stock) : update(stock);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Stock> findByProductCode(String productCode) {
        return jpaRepository.findByProductCode(productCode).map(StockMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Stock> findByProductCodeForUpdate(String productCode) {
        // Why not: readOnly にしない。行ロックは書き込みトランザクションの中で
        // 取ってこそ意味があり、読み取り専用だと同じトランザクションでの更新ができない
        return jpaRepository.findByProductCodeForUpdate(productCode).map(StockMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Stock> findAll() {
        return jpaRepository.findAll().stream()
                .map(StockMapper::toDomain)
                .sorted(Comparator.comparing(Stock::productCode))
                .toList();
    }

    private Stock insert(Stock stock) {
        try {
            return StockMapper.toDomain(jpaRepository.saveAndFlush(StockMapper.toEntity(stock)));
        } catch (DataIntegrityViolationException e) {
            // Why not: IllegalStateException を投げない。@Repository の例外変換が
            // JPA由来の例外とみなして DataAccessException に包み替えてしまい、
            // 呼び出し側が意図した型で捕捉できなくなる
            throw new DuplicateStockException(stock.productCode());
        }
    }

    private Stock update(Stock stock) {
        // 引当元は行ロック済みの在庫であり、ここで読み直しても同じ行を指す。
        // Why not: バージョンの事前照合はしない。悲観ロックで直列化しているため、
        // 「読んだ後に別処理が更新していた」状態がそもそも発生しない
        StockJpaEntity entity = jpaRepository.findById(stock.id())
                .orElseThrow(() -> new StockNotFoundException(stock.productCode()));

        entity.changeQuantities(stock.quantityOnHand(), stock.quantityAllocated());
        return StockMapper.toDomain(jpaRepository.saveAndFlush(entity));
    }
}
