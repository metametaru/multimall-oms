package com.minioms.presentation.order;

/**
 * 受注に対して、いま行える操作。
 *
 * @param operation 呼び出すパスの末尾。{@code POST /api/orders/{id}/{operation}} となる
 * @param label     画面に出す操作名
 */
record OrderActionResponse(String operation, String label) {
}
