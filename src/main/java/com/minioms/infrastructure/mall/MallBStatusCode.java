package com.minioms.infrastructure.mall;

import com.minioms.domain.order.OrderStatus;

import java.util.Arrays;
import java.util.Optional;

/**
 * モールBが返す独自ステータスコードと、OMSのステータスの対応表。
 *
 * <p>Why not: この対応をドメイン側({@link OrderStatus})に持たせない。
 * モール固有の語彙をドメインに入れると、モールが増えるたびに状態機械が
 * 汚れていくため、変換はモールごとのアダプタに閉じ込める。</p>
 */
enum MallBStatusCode {

    ACCEPTED("01", OrderStatus.NEW),
    PREPARING("02", OrderStatus.CONFIRMED),
    SHIPPED("03", OrderStatus.SHIPPED),
    CANCELLED("09", OrderStatus.CANCELLED);

    private final String code;
    private final OrderStatus orderStatus;

    MallBStatusCode(String code, OrderStatus orderStatus) {
        this.code = code;
        this.orderStatus = orderStatus;
    }

    /**
     * 未知のコードは空を返す。
     * Why not: 例外にしない。モール側の仕様追加で新コードが増えたとき、
     * 1件の未知コードが同一バッチの正常な受注まで巻き添えにするため。
     */
    static Optional<MallBStatusCode> from(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst();
    }

    OrderStatus toOrderStatus() {
        return orderStatus;
    }

    /** OMSの取込対象か。モール側で新規受付のものだけを受注ライフサイクルに乗せる */
    boolean isImportable() {
        return orderStatus == OrderStatus.NEW;
    }
}
