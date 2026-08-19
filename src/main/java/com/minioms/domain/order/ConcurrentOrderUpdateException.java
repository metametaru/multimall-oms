package com.minioms.domain.order;

import com.minioms.domain.DomainException;

/**
 * 他の処理が先に更新した受注を、古い状態のまま更新しようとしたことを表す。
 *
 * <p>同じ受注を複数のオペレーターが同時に開く運用では日常的に起きる。
 * 後勝ちで上書きすると、先に行われた出荷指示やキャンセルが消えて
 * 実在庫と食い違うため、業務上の失敗として扱う。</p>
 *
 * <p>Why not: 永続化技術の例外({@code OptimisticLockingFailureException})を
 * そのまま上位層に流さない。上位層が特定のフレームワークの例外型を知っていると、
 * 永続化の実装を差し替えたときに呼び出し側まで壊れるため。</p>
 */
public class ConcurrentOrderUpdateException extends DomainException {

    private final long orderId;

    public ConcurrentOrderUpdateException(long orderId) {
        super("受注が他の処理によって更新されています: id=%d".formatted(orderId));
        this.orderId = orderId;
    }

    public long getOrderId() {
        return orderId;
    }
}
