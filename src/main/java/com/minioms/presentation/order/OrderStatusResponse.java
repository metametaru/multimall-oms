package com.minioms.presentation.order;

import com.minioms.domain.order.OrderStatus;

/**
 * 受注ステータスの選択肢。
 *
 * @param value 絞り込みに指定する値。enum 名をそのまま使う
 * @param label 画面に出す表示名
 */
record OrderStatusResponse(OrderStatus value, String label) {
}
