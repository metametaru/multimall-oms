package com.minioms.domain.order;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 受注ステータスの状態機械。
 * 遷移可否の判定はこのenumを唯一の真実とする(判定ロジックを他所に複製しない)。
 */
public enum OrderStatus {

    /** 新規受付(モールから取込直後) */
    NEW,
    /** 確認済(オペレーターによる内容確認完了) */
    CONFIRMED,
    /** 出荷指示済(倉庫へ指示送信済み) */
    SHIPPING_INSTRUCTED,
    /** 出荷完了 */
    SHIPPED,
    /** キャンセル(終端) */
    CANCELLED,
    /** 返品(終端) */
    RETURNED;

    // Why not: 遷移ルールを各ステータスのメソッドオーバーライドで表現する案もあったが、
    // 遷移の全体像が1箇所で俯瞰できるMap定義を採用した。
    // OMSでは「どこからどこへ遷移できるか」の一覧性が保守時に最も重要なため。
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            NEW,                  EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED,            EnumSet.of(SHIPPING_INSTRUCTED, CANCELLED),
            // Why not: 出荷指示後のキャンセルは許可しない。
            // 倉庫側で既にピッキングが走っている可能性があり、システム上だけ
            // キャンセルすると実在庫と引当の不整合を生むため。
            // 実務では「出荷指示取消」という別業務フローで対応する(本デモではスコープ外)。
            SHIPPING_INSTRUCTED,  EnumSet.of(SHIPPED),
            SHIPPED,              EnumSet.of(RETURNED),
            CANCELLED,            EnumSet.noneOf(OrderStatus.class),
            RETURNED,             EnumSet.noneOf(OrderStatus.class)
    );

    /** このステータスから指定ステータスへ遷移可能か */
    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    /**
     * 遷移を実行する。不正な遷移は業務例外として弾く。
     *
     * @throws InvalidStatusTransitionException 許可されていない遷移の場合
     */
    public OrderStatus transitionTo(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(this, target);
        }
        return target;
    }

    /**
     * この状態の受注が在庫を引き当てているか。
     *
     * <p>引当は確認時に行い、出荷時に実在庫から落とす。したがって引当を
     * 抱えているのは確認済と出荷指示済の間だけで、出荷完了後は引当済ではなく
     * 実在庫が減っている。キャンセル時に引当を解除すべきかの判断もこれで決まる。</p>
     *
     * <p>Why not: この判定を在庫側やユースケースに置かない。受注の状態から導かれる
     * 業務ルールであり、状態機械の外に置くと遷移の追加時に更新が漏れる。</p>
     */
    public boolean holdsStockAllocation() {
        return this == CONFIRMED || this == SHIPPING_INSTRUCTED;
    }

    /**
     * この状態から指定の状態へ遷移したとき、在庫に何をすべきか。
     *
     * <p>引当を抱えていない状態から抱える状態へ進めば引当、抱えた状態から
     * 手放す状態へ進めば解除。ただし出荷完了だけは、約束を取り消すのではなく
     * 果たす(実在庫から落とす)ため区別する。</p>
     *
     * <p>Why not: 遷移の組み合わせを列挙しない。遷移を1本追加するたびに
     * 在庫側の対応表を書き足す必要が生まれ、書き漏らすと在庫だけが狂う。
     * 「引当を抱えているか」という状態の性質から導く。</p>
     */
    public StockEffect stockEffectOf(OrderStatus target) {
        if (!holdsStockAllocation() && target.holdsStockAllocation()) {
            return StockEffect.ALLOCATE;
        }
        if (holdsStockAllocation() && !target.holdsStockAllocation()) {
            return target == SHIPPED ? StockEffect.SHIP_OUT : StockEffect.RELEASE;
        }
        return StockEffect.NONE;
    }

    /** 終端ステータス(以降の遷移が存在しない)か */
    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
