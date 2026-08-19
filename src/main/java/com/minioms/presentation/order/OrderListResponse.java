package com.minioms.presentation.order;

import java.util.List;

/**
 * 受注一覧のレスポンス。
 *
 * @param totalCount 絞り込み条件に一致する全件数。ページャの「全何件中」に使う
 */
record OrderListResponse(List<OrderSummaryResponse> orders, long totalCount, int page, int size) {
}
