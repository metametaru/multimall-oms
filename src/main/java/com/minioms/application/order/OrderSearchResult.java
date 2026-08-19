package com.minioms.application.order;

import java.util.List;

/**
 * 受注検索の結果。
 *
 * @param orders     ページ内の受注
 * @param totalCount 絞り込み条件に一致する全件数(ページ内の件数ではない)
 */
public record OrderSearchResult(List<OrderSummary> orders, long totalCount) {

    public OrderSearchResult {
        orders = List.copyOf(orders);
    }
}
