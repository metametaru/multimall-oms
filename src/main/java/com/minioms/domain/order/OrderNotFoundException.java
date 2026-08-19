package com.minioms.domain.order;

import com.minioms.domain.DomainException;

/**
 * 指定された受注が存在しないことを表す。
 *
 * <p>Why not: {@code Optional} を返して呼び出し側に判断を委ねる形にはしない。
 * 受注操作(出荷指示・キャンセル)は対象が存在することが前提であり、
 * 存在しないIDの指定は業務上ありえない要求として扱う。</p>
 */
public class OrderNotFoundException extends DomainException {

    private final long orderId;

    public OrderNotFoundException(long orderId) {
        super("受注が存在しません: id=%d".formatted(orderId));
        this.orderId = orderId;
    }

    public long getOrderId() {
        return orderId;
    }
}
