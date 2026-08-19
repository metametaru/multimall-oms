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

    /** 終端ステータス(以降の遷移が存在しない)か */
    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
