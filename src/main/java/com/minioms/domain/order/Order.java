package com.minioms.domain.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 受注(集約ルート)。
 *
 * <p>Why not: JPAアノテーションはこのクラスに付けない。ドメインをフレームワーク非依存に
 * 保つため、永続化用のエンティティは infrastructure 層に別途置き、相互変換する。
 * ボイラープレートは増えるが、業務ルールがJPAのライフサイクル(遅延ロード・
 * ダーティチェック)に引きずられないことを優先した。</p>
 *
 * <p>Why not: 状態遷移で自身を書き換える案も採らない。イミュータブルにすることで
 * 「遷移前の受注」を安全に保持でき、監査ログや楽観ロックの扱いが単純になる。</p>
 *
 * @param id      永続化前は null(採番はDBに委ねる)
 * @param version 楽観ロック用。永続化前は 0
 */
public record Order(
        Long id,
        MallOrderKey mallOrderKey,
        OrderStatus status,
        String customerName,
        BigDecimal totalAmount,
        OffsetDateTime orderedAt,
        List<OrderItem> items,
        long version) {

    public Order {
        Objects.requireNonNull(mallOrderKey, "冪等キーは必須です");
        Objects.requireNonNull(status, "ステータスは必須です");
        Objects.requireNonNull(orderedAt, "注文日時は必須です");
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("顧客名は必須です");
        }
        if (totalAmount == null || totalAmount.signum() < 0) {
            throw new IllegalArgumentException("合計金額が不正です: " + totalAmount);
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("明細のない受注は扱えません");
        }
        items = List.copyOf(items);
    }

    /** モールAPIから取り込んだ新規受注を組み立てる */
    public static Order importedFrom(MallOrderKey mallOrderKey,
                                     String customerName,
                                     BigDecimal totalAmount,
                                     OffsetDateTime orderedAt,
                                     List<OrderItem> items) {
        return new Order(null, mallOrderKey, OrderStatus.NEW, customerName, totalAmount, orderedAt, items, 0L);
    }

    public Order confirm() {
        return transitionTo(OrderStatus.CONFIRMED);
    }

    public Order instructShipping() {
        return transitionTo(OrderStatus.SHIPPING_INSTRUCTED);
    }

    public Order ship() {
        return transitionTo(OrderStatus.SHIPPED);
    }

    public Order cancel() {
        return transitionTo(OrderStatus.CANCELLED);
    }

    public Order markReturned() {
        return transitionTo(OrderStatus.RETURNED);
    }

    /**
     * 明細の合計。
     * 送料やモール側の割引が乗るため {@link #totalAmount()} と一致しないことがある。
     * OMSはモールの提示額を正とし、この値で上書きはしない。
     */
    public BigDecimal itemsSubtotal() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 遷移可否の判定は OrderStatus に委譲する(業務ルールを二重に持たない) */
    private Order transitionTo(OrderStatus target) {
        return new Order(id, mallOrderKey, status.transitionTo(target),
                customerName, totalAmount, orderedAt, items, version);
    }
}
