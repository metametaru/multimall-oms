package com.minioms.application.order;

import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderNotFoundException;

/**
 * 受注を参照する。オペレーターの一覧画面と詳細画面の入口。
 *
 * <p>Why not: Controller から参照ポートを直接呼ばない。一覧と詳細で参照先
 * (読み取りモデルと集約)が分かれていることを presentation 層に知らせないため、
 * 入口はこのユースケースに一本化する。</p>
 */
public class FindOrdersUseCase {

    private final OrderSearchQuery orderSearchQuery;
    private final OrderRepository orderRepository;

    public FindOrdersUseCase(OrderSearchQuery orderSearchQuery, OrderRepository orderRepository) {
        this.orderSearchQuery = orderSearchQuery;
        this.orderRepository = orderRepository;
    }

    public OrderSearchResult search(OrderSearchCriteria criteria) {
        return orderSearchQuery.search(criteria);
    }

    /**
     * 明細を含む受注を1件取得する。
     *
     * @throws OrderNotFoundException 指定IDの受注が存在しない場合
     */
    public Order findById(long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
