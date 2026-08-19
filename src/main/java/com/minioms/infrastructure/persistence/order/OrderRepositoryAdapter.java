package com.minioms.infrastructure.persistence.order;

import com.minioms.application.order.OrderRepository;
import com.minioms.domain.order.DuplicateMallOrderException;
import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link OrderRepository} のJPA実装。
 * Spring Data の例外をドメインの語彙に翻訳し、上位層にフレームワークを漏らさない。
 */
@Repository
@RequiredArgsConstructor
class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    @Override
    @Transactional
    public Order save(Order order) {
        return order.id() == null ? insert(order) : update(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByMallOrderKey(MallOrderKey mallOrderKey) {
        return jpaRepository
                .findByMallIdAndMallOrderNumber(mallOrderKey.mallId(), mallOrderKey.mallOrderNumber())
                .map(OrderMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(long orderId) {
        return jpaRepository.findWithItemsById(orderId).map(OrderMapper::toDomain);
    }

    private Order insert(Order order) {
        try {
            // Why not: save() ではなく saveAndFlush()。save() だけでは制約違反が
            // トランザクションコミット時まで遅延し、ここで捕捉できないため
            return OrderMapper.toDomain(jpaRepository.saveAndFlush(OrderMapper.toEntity(order)));
        } catch (DataIntegrityViolationException e) {
            // Why not: 事前の存在チェックだけに頼らない。バッチ多重起動時は
            // チェックとINSERTの間に別プロセスが挿入しうるため、DB制約を最終防衛線とする
            throw new DuplicateMallOrderException(order.mallOrderKey());
        }
    }

    private Order update(Order order) {
        OrderJpaEntity entity = jpaRepository.findById(order.id())
                .orElseThrow(() -> new OptimisticLockingFailureException(
                        "更新対象の受注が存在しません: id=" + order.id()));

        if (!entity.getVersion().equals(order.version())) {
            throw new OptimisticLockingFailureException(
                    "受注が他の処理に更新されています: id=%d".formatted(order.id()));
        }

        // Why not: 明細や金額は更新対象にしない。モールから取り込んだ受注内容を
        // OMS側で書き換える業務は存在せず、変わるのはステータスだけであるため
        entity.changeStatus(order.status());
        return OrderMapper.toDomain(jpaRepository.saveAndFlush(entity));
    }
}
