package com.minioms.application.order;

import com.minioms.domain.order.ConcurrentOrderUpdateException;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.UnaryOperator;

/**
 * 受注を業務フローに沿って進める(確認・出荷指示・出荷完了・キャンセル・返品)。
 *
 * <p>Why not: 「ステータスを指定の値に変更する」という汎用の操作は用意しない。
 * 呼び出し側が状態機械を知っている前提になり、出荷指示やキャンセルという
 * 業務操作の意味がAPIから消えるため、操作ごとにメソッドを分ける。</p>
 *
 * <p>Why not: 遷移可否の判定をここに書かない。判定はドメインの状態機械が
 * 唯一の真実であり、ユースケースは対象の取り出しと保存だけを担う。</p>
 */
public class OrderLifecycleUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleUseCase.class);

    private final OrderRepository orderRepository;

    public OrderLifecycleUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** 内容確認が済んだ受注を確認済にする */
    public Order confirm(long orderId, long expectedVersion) {
        return apply(orderId, expectedVersion, Order::confirm);
    }

    /** 倉庫へ出荷を指示する。これ以降はキャンセルできない */
    public Order instructShipping(long orderId, long expectedVersion) {
        return apply(orderId, expectedVersion, Order::instructShipping);
    }

    /** 出荷完了を記録する */
    public Order ship(long orderId, long expectedVersion) {
        return apply(orderId, expectedVersion, Order::ship);
    }

    /** 受注をキャンセルする */
    public Order cancel(long orderId, long expectedVersion) {
        return apply(orderId, expectedVersion, Order::cancel);
    }

    /** 出荷済みの受注の返品を記録する */
    public Order markReturned(long orderId, long expectedVersion) {
        return apply(orderId, expectedVersion, Order::markReturned);
    }

    private Order apply(long orderId, long expectedVersion, UnaryOperator<Order> transition) {
        Order current = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Why not: 保存時のバージョン照合(DB側)だけに頼らない。ここで弾くことで
        // 「操作は成功したが対象が古かった」ケースを、業務の失敗として明示できる。
        // DB側の照合は、この判定と保存の間に割り込む更新に対する最終防衛線として残す
        if (current.version() != expectedVersion) {
            throw new ConcurrentOrderUpdateException(orderId);
        }

        Order updated = orderRepository.save(transition.apply(current));
        log.info("受注のステータスを変更しました: id={} {} → {}", orderId, current.status(), updated.status());
        return updated;
    }
}
