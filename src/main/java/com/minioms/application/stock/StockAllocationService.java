package com.minioms.application.stock;

import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderItem;
import com.minioms.domain.order.StockEffect;
import com.minioms.domain.stock.Stock;
import com.minioms.domain.stock.StockNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 受注の在庫を引き当て・解除・出荷する。
 *
 * <p>何をすべきかの判断({@link StockEffect})は受注の状態機械が持ち、
 * ここは「どう在庫に反映するか」だけを担う。</p>
 */
public class StockAllocationService {

    private static final Logger log = LoggerFactory.getLogger(StockAllocationService.class);

    private final StockRepository stockRepository;

    public StockAllocationService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /** 受注のステータス遷移に対応する在庫操作を実行する */
    public void apply(StockEffect effect, Order order) {
        switch (effect) {
            case NONE -> {
                // 在庫は動かない
            }
            case ALLOCATE -> update(order, Stock::allocate, "引当");
            case RELEASE -> update(order, Stock::release, "引当解除");
            case SHIP_OUT -> update(order, Stock::shipOut, "出荷");
        }
    }

    private void update(Order order, StockOperation operation, String operationName) {
        requiredQuantities(order).forEach((productCode, quantity) -> {
            Stock stock = stockRepository.findByProductCodeForUpdate(productCode)
                    .orElseThrow(() -> new StockNotFoundException(productCode));

            stockRepository.save(operation.apply(stock, quantity));
            log.info("在庫を{}しました: 商品={} 数量={} 受注={}", operationName, productCode, quantity, order.id());
        });
    }

    /**
     * 受注が必要とする商品コードごとの数量。
     *
     * <p>Why not: 明細をそのまま1件ずつ処理しない。同じ商品を複数の明細に分けて
     * 送ってくるモールがあり、明細単位で処理すると同じ在庫行を何度もロック・更新し、
     * 在庫不足の判定も明細ごとになって不足の理由が分かりにくくなる。</p>
     *
     * <p>Why not: 順序を明細の並び順に任せない。商品コード順に固定することで、
     * 複数の受注が同じ商品群を同時に処理してもロックを取る順番が揃い、
     * 互いのロックを待ち合うデッドロックを避けられる。</p>
     */
    private static SortedMap<String, Integer> requiredQuantities(Order order) {
        SortedMap<String, Integer> quantities = new TreeMap<>();
        for (OrderItem item : order.items()) {
            quantities.merge(item.productCode(), item.quantity(), Integer::sum);
        }
        return quantities;
    }

    @FunctionalInterface
    private interface StockOperation {
        Stock apply(Stock stock, int quantity);
    }
}
