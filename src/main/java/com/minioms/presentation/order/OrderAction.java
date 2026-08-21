package com.minioms.presentation.order;

import com.minioms.domain.order.OrderStatus;

import java.util.Arrays;
import java.util.List;

/**
 * 受注に対する業務操作と、それが到達させるステータスの対応。
 *
 * <p>「どこからどこへ進めるか」は状態機械({@link OrderStatus})が持ち、ここが持つのは
 * 「その遷移をAPIでは何と呼ぶか」だけ。遷移可否の判定は一切していない。</p>
 *
 * <p>Why not: 画面側にこの対応表を置かない。画面が「NEWなら確認とキャンセル」と
 * 知っている形にすると遷移ルールが二重化し、遷移を1本追加したときに
 * 画面だけ古いまま残る。サーバーが「いま押せる操作」を返し、画面はそれを描くだけにする。</p>
 */
enum OrderAction {

    CONFIRM(OrderStatus.CONFIRMED, "confirmation", "確認"),
    INSTRUCT_SHIPPING(OrderStatus.SHIPPING_INSTRUCTED, "shipping-instruction", "出荷指示"),
    SHIP(OrderStatus.SHIPPED, "shipment", "出荷完了"),
    CANCEL(OrderStatus.CANCELLED, "cancellation", "キャンセル"),
    MARK_RETURNED(OrderStatus.RETURNED, "return", "返品");

    private final OrderStatus target;
    private final String operation;
    private final String label;

    OrderAction(OrderStatus target, String operation, String label) {
        this.target = target;
        this.operation = operation;
        this.label = label;
    }

    /** 指定ステータスの受注に対して、いま行える操作 */
    static List<OrderActionResponse> availableFrom(OrderStatus status) {
        return Arrays.stream(values())
                .filter(action -> status.allowedTransitions().contains(action.target))
                .map(action -> new OrderActionResponse(action.operation, action.label))
                .toList();
    }
}
