package com.minioms.application.order;

import com.minioms.domain.order.ConcurrentOrderUpdateException;
import com.minioms.domain.order.InvalidStatusTransitionException;
import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderItem;
import com.minioms.domain.order.OrderNotFoundException;
import com.minioms.domain.order.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final OrderLifecycleUseCase useCase = new OrderLifecycleUseCase(orderRepository);

    private Order 受注を用意する(OrderStatus status, long version) {
        return orderRepository.store(new Order(1L, new MallOrderKey(MALL_A, "A-001"), status, "山田太郎",
                new BigDecimal("5400"), OffsetDateTime.parse("2026-08-19T10:00:00+09:00"),
                List.of(OrderItem.of("SKU-001", "テスト商品", new BigDecimal("2700"), 2)), version));
    }

    @Nested
    @DisplayName("業務操作")
    class 業務操作 {

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

    // --- テストダブル -------------------------------------------------------

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
