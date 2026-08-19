package com.minioms.application.order;

import com.minioms.application.TransactionRunner;
import com.minioms.application.stock.StockAllocationService;
import com.minioms.domain.order.ConcurrentOrderUpdateException;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderNotFoundException;
import com.minioms.domain.order.StockEffect;
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
 * 唯一の真実であり、ユースケースは対象の取り出しと保存だけを担う。
 * 遷移が在庫に与える影響({@link StockEffect})も同様に状態機械から受け取る。</p>
 *
 * <p>受注の更新と在庫の反映は必ず同一トランザクションで行う。「確認済だが在庫は
 * 押さえられていない」「引当済だが受注は未確認」という状態は、実在庫と帳簿の
 * 食い違いに直結し、欠品や二重売りとして表面化する。</p>
 */
public class OrderLifecycleUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrderLifecycleUseCase.class);

    private final OrderRepository orderRepository;
    private final StockAllocationService stockAllocationService;
    private final TransactionRunner transactionRunner;

    public OrderLifecycleUseCase(OrderRepository orderRepository,
                                 StockAllocationService stockAllocationService,
                                 TransactionRunner transactionRunner) {
        this.orderRepository = orderRepository;
        this.stockAllocationService = stockAllocationService;
        this.transactionRunner = transactionRunner;
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
        return transactionRunner.execute(() -> {
            Order current = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            // Why not: 保存時のバージョン照合(DB側)だけに頼らない。ここで弾くことで
            // 「操作は成功したが対象が古かった」ケースを、業務の失敗として明示できる。
            // DB側の照合は、この判定と保存の間に割り込む更新に対する最終防衛線として残す
            if (current.version() != expectedVersion) {
                throw new ConcurrentOrderUpdateException(orderId);
            }

            Order updated = orderRepository.save(transition.apply(current));

            // 在庫の反映は受注の保存と同一トランザクション。在庫不足で失敗した場合は
            // 受注のステータス更新ごと巻き戻り、「確認済だが在庫が無い」状態を残さない
            stockAllocationService.apply(current.status().stockEffectOf(updated.status()), updated);

            log.info("受注のステータスを変更しました: id={} {} → {}", orderId, current.status(), updated.status());
            return updated;
        });
    }
}
