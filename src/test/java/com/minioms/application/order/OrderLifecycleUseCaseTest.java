package com.minioms.application.order;

import com.minioms.application.TransactionRunner;
import com.minioms.application.stock.StockAllocationService;
import com.minioms.application.stock.StockRepository;
import com.minioms.domain.order.ConcurrentOrderUpdateException;
import com.minioms.domain.order.InvalidStatusTransitionException;
import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderItem;
import com.minioms.domain.order.OrderNotFoundException;
import com.minioms.domain.order.OrderStatus;
import com.minioms.domain.stock.InsufficientStockException;
import com.minioms.domain.stock.Stock;
import com.minioms.domain.stock.StockNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受注ライフサイクル操作の仕様。
 *
 * <p>遷移可否そのものはドメイン({@code OrderStatus})の仕様であり、ここで固定するのは
 * 「操作の対象を取り出し、他のオペレーターの更新を上書きしないことを確かめ、保存する」
 * という手順。特に楽観ロックは、同じ受注を複数人が同時に開く運用で
 * 出荷指示とキャンセルが競合したときの実害が大きい。</p>
 */
@DisplayName("受注ライフサイクル操作")
class OrderLifecycleUseCaseTest {

    private static final long MALL_A = 1L;

    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    private final InMemoryStockRepository stockRepository = new InMemoryStockRepository();
    private final OrderLifecycleUseCase useCase = new OrderLifecycleUseCase(
            orderRepository,
            new StockAllocationService(stockRepository),
            new DirectTransactionRunner());

    private Order 受注を用意する(OrderStatus status, long version) {
        return 受注を用意する(status, version, OrderItem.of("SKU-001", "テスト商品", new BigDecimal("2700"), 2));
    }

    private Order 受注を用意する(OrderStatus status, long version, OrderItem... items) {
        return orderRepository.store(new Order(1L, new MallOrderKey(MALL_A, "A-001"), status, "山田太郎",
                new BigDecimal("5400"), OffsetDateTime.parse("2026-08-19T10:00:00+09:00"),
                List.of(items), version));
    }

    private void 在庫を用意する(String productCode, int 実在庫, int 引当済) {
        stockRepository.store(new Stock(1L, productCode, 実在庫, 引当済, 0L));
    }

    private Stock 在庫(String productCode) {
        return stockRepository.findByProductCode(productCode).orElseThrow();
    }

    @Nested
    @DisplayName("業務操作")
    class 業務操作 {

        @BeforeEach
        void 在庫の不足で失敗しないようにする() {
            在庫を用意する("SKU-001", 100, 50);
        }

        @Test
        void 新規受注を確認済にできる() {
            受注を用意する(OrderStatus.NEW, 0L);

            assertThat(useCase.confirm(1L, 0L).status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(orderRepository.findById(1L)).get()
                    .extracting(Order::status).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        void 確認済の受注に出荷指示を出せる() {
            受注を用意する(OrderStatus.CONFIRMED, 1L);

            assertThat(useCase.instructShipping(1L, 1L).status()).isEqualTo(OrderStatus.SHIPPING_INSTRUCTED);
        }

        @Test
        void 出荷指示済の受注を出荷完了にできる() {
            受注を用意する(OrderStatus.SHIPPING_INSTRUCTED, 2L);

            assertThat(useCase.ship(1L, 2L).status()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        void 出荷指示前の受注はキャンセルできる() {
            受注を用意する(OrderStatus.NEW, 0L);

            assertThat(useCase.cancel(1L, 0L).status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void 出荷完了後の受注は返品として扱える() {
            受注を用意する(OrderStatus.SHIPPED, 3L);

            assertThat(useCase.markReturned(1L, 3L).status()).isEqualTo(OrderStatus.RETURNED);
        }
    }

    @Nested
    @DisplayName("業務ルールの委譲")
    class 業務ルールの委譲 {

        @BeforeEach
        void 在庫の不足で失敗しないようにする() {
            在庫を用意する("SKU-001", 100, 50);
        }

        @Test
        void 出荷指示済みの受注はキャンセルできない() {
            // 遷移可否の判断はドメインの状態機械が持つ。ユースケースは判定を複製しない
            受注を用意する(OrderStatus.SHIPPING_INSTRUCTED, 2L);

            assertThatThrownBy(() -> useCase.cancel(1L, 2L))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void 遷移に失敗した受注は保存されない() {
            受注を用意する(OrderStatus.SHIPPING_INSTRUCTED, 2L);

            assertThatThrownBy(() -> useCase.cancel(1L, 2L)).isInstanceOf(InvalidStatusTransitionException.class);
            assertThat(orderRepository.findById(1L)).get()
                    .extracting(Order::status).isEqualTo(OrderStatus.SHIPPING_INSTRUCTED);
        }

        @Test
        void 存在しない受注への操作は例外になる() {
            assertThatThrownBy(() -> useCase.confirm(999L, 0L))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("同時更新の防止")
    class 同時更新の防止 {

        @BeforeEach
        void 在庫の不足で失敗しないようにする() {
            在庫を用意する("SKU-001", 100, 50);
        }

        @Test
        void 古いバージョンを指定した操作は拒否される() {
            // 一覧を開いたまま別のオペレーターが先に進めた受注を、
            // 古い画面の状態で上書きすると操作が消える
            受注を用意する(OrderStatus.CONFIRMED, 5L);

            assertThatThrownBy(() -> useCase.cancel(1L, 4L))
                    .isInstanceOf(ConcurrentOrderUpdateException.class);
        }

        @Test
        void 競合した操作は受注を変更しない() {
            受注を用意する(OrderStatus.CONFIRMED, 5L);

            assertThatThrownBy(() -> useCase.cancel(1L, 4L)).isInstanceOf(ConcurrentOrderUpdateException.class);
            assertThat(orderRepository.findById(1L)).get()
                    .extracting(Order::status).isEqualTo(OrderStatus.CONFIRMED);
        }
    }

    @Nested
    @DisplayName("在庫の引当")
    class 在庫の引当 {

        @Test
        void 確認すると明細の数量が引き当てられる() {
            在庫を用意する("SKU-001", 10, 0);
            受注を用意する(OrderStatus.NEW, 0L);

            useCase.confirm(1L, 0L);

            assertThat(在庫("SKU-001").quantityAllocated()).isEqualTo(2);
            assertThat(在庫("SKU-001").quantityOnHand()).isEqualTo(10);
        }

        @Test
        void 在庫が足りなければ確認できない() {
            在庫を用意する("SKU-001", 10, 9);
            受注を用意する(OrderStatus.NEW, 0L);

            assertThatThrownBy(() -> useCase.confirm(1L, 0L))
                    .isInstanceOf(InsufficientStockException.class);
        }

        @Test
        void 在庫マスタに無い商品は確認できない() {
            // 「在庫を切らしている」と「そもそも在庫管理されていない」は対処が違う
            受注を用意する(OrderStatus.NEW, 0L);

            assertThatThrownBy(() -> useCase.confirm(1L, 0L))
                    .isInstanceOf(StockNotFoundException.class);
        }

        @Test
        void 同じ商品が複数明細に分かれていても合算して引き当てる() {
            // 同じ商品を複数の明細に分けて送ってくるモールがある
            在庫を用意する("SKU-001", 10, 0);
            受注を用意する(OrderStatus.NEW, 0L,
                    OrderItem.of("SKU-001", "テスト商品", new BigDecimal("2700"), 2),
                    OrderItem.of("SKU-001", "テスト商品", new BigDecimal("2700"), 3));

            useCase.confirm(1L, 0L);

            assertThat(在庫("SKU-001").quantityAllocated()).isEqualTo(5);
        }

        @Test
        void 出荷指示では在庫は動かない() {
            在庫を用意する("SKU-001", 10, 2);
            受注を用意する(OrderStatus.CONFIRMED, 1L);

            useCase.instructShipping(1L, 1L);

            assertThat(在庫("SKU-001").quantityAllocated()).isEqualTo(2);
            assertThat(在庫("SKU-001").quantityOnHand()).isEqualTo(10);
        }

        @Test
        void 出荷完了で引当が実在庫から落ちる() {
            在庫を用意する("SKU-001", 10, 2);
            受注を用意する(OrderStatus.SHIPPING_INSTRUCTED, 2L);

            useCase.ship(1L, 2L);

            assertThat(在庫("SKU-001").quantityOnHand()).isEqualTo(8);
            assertThat(在庫("SKU-001").quantityAllocated()).isZero();
        }

        @Test
        void 引当済の受注をキャンセルすると引当が解除される() {
            在庫を用意する("SKU-001", 10, 2);
            受注を用意する(OrderStatus.CONFIRMED, 1L);

            useCase.cancel(1L, 1L);

            assertThat(在庫("SKU-001").quantityAllocated()).isZero();
            assertThat(在庫("SKU-001").quantityOnHand()).isEqualTo(10);
        }

        @Test
        void 確認前の受注をキャンセルしても在庫は動かない() {
            // まだ引き当てていないので解除するものが無い
            在庫を用意する("SKU-001", 10, 0);
            受注を用意する(OrderStatus.NEW, 0L);

            useCase.cancel(1L, 0L);

            assertThat(在庫("SKU-001").quantityAllocated()).isZero();
        }

        @Test
        void 返品しても在庫は戻さない() {
            // 返品されたモノは検品を経てから戻す(検品フローはスコープ外)。
            // 自動で戻すと不良品を引き当て可能な在庫として数えてしまう
            在庫を用意する("SKU-001", 8, 0);
            受注を用意する(OrderStatus.SHIPPED, 3L);

            useCase.markReturned(1L, 3L);

            assertThat(在庫("SKU-001").quantityOnHand()).isEqualTo(8);
        }
    }

    // --- テストダブル -------------------------------------------------------

    /** トランザクション境界そのものの検証は結合テストで行う。ここでは処理をそのまま実行する */
    private static final class DirectTransactionRunner implements TransactionRunner {
        @Override
        public <T> T execute(Supplier<T> action) {
            return action.get();
        }
    }

    private static final class InMemoryStockRepository implements StockRepository {

        private final Map<String, Stock> stored = new HashMap<>();

        void store(Stock stock) {
            stored.put(stock.productCode(), stock);
        }

        @Override
        public Stock save(Stock stock) {
            stored.put(stock.productCode(), stock);
            return stock;
        }

        @Override
        public Optional<Stock> findByProductCode(String productCode) {
            return Optional.ofNullable(stored.get(productCode));
        }

        @Override
        public Optional<Stock> findByProductCodeForUpdate(String productCode) {
            return findByProductCode(productCode);
        }

        @Override
        public List<Stock> findAll() {
            return List.copyOf(stored.values());
        }
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final Map<Long, Order> stored = new HashMap<>();

        Order store(Order order) {
            stored.put(order.id(), order);
            return order;
        }

        @Override
        public Order save(Order order) {
            Order current = stored.get(order.id());
            if (current != null && current.version() != order.version()) {
                throw new ConcurrentOrderUpdateException(order.id());
            }
            // 実DBと同じく、更新のたびにバージョンが進む
            Order persisted = new Order(order.id(), order.mallOrderKey(), order.status(), order.customerName(),
                    order.totalAmount(), order.orderedAt(), order.items(), order.version() + 1);
            stored.put(order.id(), persisted);
            return persisted;
        }

        @Override
        public Optional<Order> findByMallOrderKey(MallOrderKey mallOrderKey) {
            return stored.values().stream()
                    .filter(order -> order.mallOrderKey().equals(mallOrderKey))
                    .findFirst();
        }

        @Override
        public Optional<Order> findById(long orderId) {
            return Optional.ofNullable(stored.get(orderId));
        }
    }
}
