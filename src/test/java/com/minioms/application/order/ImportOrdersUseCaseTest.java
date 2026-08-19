package com.minioms.application.order;

import com.minioms.domain.order.DuplicateMallOrderException;
import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受注取込ユースケースの仕様。
 *
 * <p>取込バッチは「止まらないこと」が最優先の業務要件になる。1件の異常データや
 * 1モールのAPI障害で全モールの取込が停止すると、出荷業務そのものが止まるため。
 * このテストはその境界を仕様として固定する。</p>
 */
@DisplayName("受注取込")
class ImportOrdersUseCaseTest {

    private static final long MALL_A = 1L;
    private static final long MALL_B = 2L;
    private static final OffsetDateTime SINCE = OffsetDateTime.parse("2026-08-19T00:00:00+09:00");

    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();

    private static Order order(long mallId, String mallOrderNumber) {
        return Order.importedFrom(
                new MallOrderKey(mallId, mallOrderNumber),
                "山田太郎",
                new BigDecimal("5400"),
                OffsetDateTime.parse("2026-08-19T10:00:00+09:00"),
                List.of(OrderItem.of("SKU-001", "テスト商品", new BigDecimal("2000"), 2)));
    }

    private ImportSummary importFrom(MallOrderClient... clients) {
        return new ImportOrdersUseCase(List.of(clients), orderRepository).execute(SINCE);
    }

    @Nested
    @DisplayName("正常な取込")
    class 正常な取込 {

        @Test
        void モールから取得した受注を保存する() {
            ImportSummary summary = importFrom(
                    new StubMallClient(MALL_A, List.of(order(MALL_A, "A-001"), order(MALL_A, "A-002"))));

            assertThat(summary.totalImported()).isEqualTo(2);
            assertThat(orderRepository.count()).isEqualTo(2);
        }

        @Test
        void 複数のモールから取り込む() {
            ImportSummary summary = importFrom(
                    new StubMallClient(MALL_A, List.of(order(MALL_A, "A-001"))),
                    new StubMallClient(MALL_B, List.of(order(MALL_B, "B-001"))));

            assertThat(summary.totalImported()).isEqualTo(2);
            assertThat(summary.results()).hasSize(2);
        }

        @Test
        void 取得件数が0でも正常に終わる() {
            ImportSummary summary = importFrom(new StubMallClient(MALL_A, List.of()));

            assertThat(summary.totalImported()).isZero();
            assertThat(summary.hasFailure()).isFalse();
        }
    }

    @Nested
    @DisplayName("冪等性")
    class 冪等性 {

        @Test
        void 既に取込済みの受注はスキップとして数え失敗にはしない() {
            // 取込ウィンドウは前回と重ねて取得する運用のため、重複は日常的に発生する。
            // これを異常として扱うと、正常な運用でアラートが鳴り続ける
            orderRepository.save(order(MALL_A, "A-001"));

            ImportSummary summary = importFrom(
                    new StubMallClient(MALL_A, List.of(order(MALL_A, "A-001"), order(MALL_A, "A-002"))));

            assertThat(summary.totalSkipped()).isEqualTo(1);
            assertThat(summary.totalImported()).isEqualTo(1);
            assertThat(summary.totalFailed()).isZero();
            assertThat(summary.hasFailure()).isFalse();
        }
    }

    @Nested
    @DisplayName("障害時も取込を止めない")
    class 障害時も取込を止めない {

        @Test
        void 受注1件の保存失敗は他の受注の取込を止めない() {
            orderRepository.failOn("A-002");

            ImportSummary summary = importFrom(
                    new StubMallClient(MALL_A, List.of(order(MALL_A, "A-001"), order(MALL_A, "A-002"), order(MALL_A, "A-003"))));

            assertThat(summary.totalImported()).isEqualTo(2);
            assertThat(summary.totalFailed()).isEqualTo(1);
            assertThat(summary.hasFailure()).isTrue();
        }

        @Test
        void あるモールのAPI障害は他モールの取込を止めない() {
            ImportSummary summary = importFrom(
                    new FailingMallClient(MALL_A),
                    new StubMallClient(MALL_B, List.of(order(MALL_B, "B-001"))));

            assertThat(summary.totalImported()).isEqualTo(1);
            assertThat(summary.hasFailure()).isTrue();
            assertThat(summary.failedMallIds()).containsExactly(MALL_A);
        }

        @Test
        void 全モールが障害でも例外を投げずに結果を返す() {
            // 呼び出し元(スケジューラ)が結果を記録できるよう、例外ではなく値で返す
            ImportSummary summary = importFrom(new FailingMallClient(MALL_A), new FailingMallClient(MALL_B));

            assertThat(summary.totalImported()).isZero();
            assertThat(summary.failedMallIds()).containsExactly(MALL_A, MALL_B);
        }
    }

    // --- テストダブル -------------------------------------------------------

    private record StubMallClient(long mallId, List<Order> orders) implements MallOrderClient {
        @Override
        public List<Order> fetchOrdersPlacedSince(OffsetDateTime since) {
            return orders;
        }
    }

    private record FailingMallClient(long mallId) implements MallOrderClient {
        @Override
        public List<Order> fetchOrdersPlacedSince(OffsetDateTime since) {
            throw new IllegalStateException("モールAPIに接続できません(テスト用)");
        }
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final Map<MallOrderKey, Order> stored = new LinkedHashMap<>();
        private final Set<String> failingOrderNumbers = new HashSet<>();
        private long sequence = 0;

        void failOn(String mallOrderNumber) {
            failingOrderNumbers.add(mallOrderNumber);
        }

        int count() {
            return stored.size();
        }

        @Override
        public Order save(Order order) {
            if (failingOrderNumbers.contains(order.mallOrderKey().mallOrderNumber())) {
                throw new IllegalStateException("DBに接続できません(テスト用)");
            }
            if (stored.containsKey(order.mallOrderKey())) {
                throw new DuplicateMallOrderException(order.mallOrderKey());
            }
            Order persisted = new Order(++sequence, order.mallOrderKey(), order.status(), order.customerName(),
                    order.totalAmount(), order.orderedAt(), new ArrayList<>(order.items()), order.version());
            stored.put(order.mallOrderKey(), persisted);
            return persisted;
        }

        @Override
        public Optional<Order> findByMallOrderKey(MallOrderKey mallOrderKey) {
            return Optional.ofNullable(stored.get(mallOrderKey));
        }
    }
}
