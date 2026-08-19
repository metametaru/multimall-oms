package com.minioms.infrastructure.persistence.order;

import com.minioms.application.order.OrderSearchCriteria;
import com.minioms.application.order.OrderSearchQuery;
import com.minioms.application.order.OrderSearchResult;
import com.minioms.application.order.OrderSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OrderSearchQuery} のJPA実装。
 *
 * <p>Why not: Spring Data の {@code Page} をそのまま application 層に返さない。
 * ページングの都合でユースケースがフレームワークの型に縛られるため、
 * 件数と要素だけを持つ自前の結果型に詰め替える。</p>
 */
@Repository
@RequiredArgsConstructor
class OrderSearchQueryAdapter implements OrderSearchQuery {

    private final OrderJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public OrderSearchResult search(OrderSearchCriteria criteria) {
        Page<OrderSummary> page = jpaRepository.search(
                criteria.status(),
                criteria.mallId(),
                PageRequest.of(criteria.page(), criteria.size()));

        return new OrderSearchResult(page.getContent(), page.getTotalElements());
    }
}
