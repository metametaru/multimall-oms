package com.minioms.domain.order;

/**
 * 許可されていないステータス遷移が要求されたことを表す業務例外。
 * HTTPステータスへの変換は presentation 層の責務(ここでは行わない)。
 */
public class InvalidStatusTransitionException extends RuntimeException {

    private final OrderStatus from;
    private final OrderStatus to;

    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("不正なステータス遷移: %s → %s".formatted(from, to));
        this.from = from;
        this.to = to;
    }

    public OrderStatus getFrom() {
        return from;
    }

    public OrderStatus getTo() {
        return to;
    }
}
