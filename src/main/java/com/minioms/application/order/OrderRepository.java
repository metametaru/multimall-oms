package com.minioms.application.order;

import com.minioms.domain.order.DuplicateMallOrderException;
import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;

import java.util.Optional;

/**
 * 受注の永続化ポート。実装は infrastructure 層が担う。
 *
 * <p>Why not: Spring Data のリポジトリインターフェースを application 層に直接置く案は
 * 採らない。application がフレームワークの型(Page、Example など)に依存すると、
 * ユースケースの意図が永続化技術の都合に引きずられるため。</p>
 */
public interface OrderRepository {

    /**
     * 受注を保存する。IDを持たない受注は新規登録、持つ受注は更新となる。
     *
     * @return 採番済みID・更新後バージョンを反映した受注
     * @throws DuplicateMallOrderException 新規登録で冪等キーが既に存在した場合
     */
    Order save(Order order);

    Optional<Order> findByMallOrderKey(MallOrderKey mallOrderKey);
}
