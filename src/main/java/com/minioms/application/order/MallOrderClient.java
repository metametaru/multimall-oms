package com.minioms.application.order;

import com.minioms.domain.order.Order;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 1つのECモールから受注を取得するポート。実装は infrastructure 層が担う。
 *
 * <p>Why not: モール固有のレスポンス(JSON / XML / 独自ステータスコード)を
 * この層まで持ち上げない。実装側でドメインの {@link Order} へ変換しきることで、
 * モールが増えてもユースケースを書き換えずに済む(腐敗防止層)。</p>
 */
public interface MallOrderClient {

    /** このクライアントが担当するモールのID */
    long mallId();

    /**
     * 指定日時以降に注文された受注を取得する。
     *
     * <p>取得ウィンドウを前回と重ねても安全である。重複は冪等キーで弾かれるため、
     * 取りこぼしを防ぐ方を優先してよい。</p>
     */
    List<Order> fetchOrdersPlacedSince(OffsetDateTime since);
}
