package com.minioms.domain.order;

import com.minioms.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受注(集約ルート)の仕様。
 * 状態遷移の可否そのものは OrderStatusTest が仕様であり、
 * ここでは「受注が状態機械に委譲していること」と受注固有のルールを表現する。
 */
@DisplayName("受注")
class OrderTest {

    private static final MallOrderKey MALL_ORDER_KEY = new MallOrderKey(1L, "A-20260819-0001");
    private static final OffsetDateTime ORDERED_AT = OffsetDateTime.parse("2026-08-19T10:00:00+09:00");

    /** 単価2,000円 × 2点 = 明細合計4,000円。モール提示の合計は送料込みの5,400円 */
    private static Order importedOrder() {
        return Order.importedFrom(
                MALL_ORDER_KEY,
                "山田太郎",
                new BigDecimal("5400"),
                ORDERED_AT,
                List.of(OrderItem.of("SKU-001", "テスト商品", new BigDecimal("2000"), 2)));
    }

    @Nested
    @DisplayName("モールからの取込")
    class モールからの取込 {

        @Test
        void 取り込んだ直後の受注は新規受付状態である() {
            assertThat(importedOrder().status()).isEqualTo(OrderStatus.NEW);
        }

        @Test
        void 取り込んだ直後は採番前のためIDを持たない() {
            assertThat(importedOrder().id()).isNull();
        }

        @Test
        void 明細のない受注は取り込めない() {
            assertThatThrownBy(() -> Order.importedFrom(
                    MALL_ORDER_KEY, "山田太郎", new BigDecimal("5400"), ORDERED_AT, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 数量が0以下の明細は作れない() {
            assertThatThrownBy(() -> OrderItem.of("SKU-001", "テスト商品", new BigDecimal("2000"), 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("金額")
    class 金額 {

        @Test
        void 合計金額はモールが提示した値をそのまま保持する() {
            // 送料・モール側の割引が乗るため、明細合計と一致しないことがある。
            // OMSはモールの提示額を正とし、再計算しない
            Order order = importedOrder();
            assertThat(order.totalAmount()).isEqualByComparingTo("5400");
            assertThat(order.itemsSubtotal()).isEqualByComparingTo("4000");
        }
    }

    @Nested
    @DisplayName("状態遷移")
    class 状態遷移 {

        @Test
        void 確認から出荷完了まで一連の業務フローを進められる() {
            Order shipped = importedOrder().confirm().instructShipping().ship();
            assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        void 遷移しても元の受注インスタンスは変化しない() {
            Order original = importedOrder();
            Order confirmed = original.confirm();

            assertThat(original.status()).isEqualTo(OrderStatus.NEW);
            assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(confirmed).isNotSameAs(original);
        }

        @Test
        void 遷移しても冪等キーと明細は引き継がれる() {
            Order confirmed = importedOrder().confirm();

            assertThat(confirmed.mallOrderKey()).isEqualTo(MALL_ORDER_KEY);
            assertThat(confirmed.items()).isEqualTo(importedOrder().items());
        }

        @Test
        void 出荷指示済みの受注はキャンセルできない() {
            Order instructed = importedOrder().confirm().instructShipping();

            assertThatThrownBy(instructed::cancel)
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void 業務ルール違反はDomainExceptionとして扱える() {
            // presentation層はDomainExceptionを起点にHTTPステータスへ変換するため、
            // 個別の例外型を知らなくても捕捉できる必要がある
            assertThatThrownBy(() -> importedOrder().ship())
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("冪等キー")
    class 冪等キー {

        @Test
        void 同一モールの同一注文番号なら等価とみなす() {
            assertThat(new MallOrderKey(1L, "A-0001")).isEqualTo(new MallOrderKey(1L, "A-0001"));
        }

        @Test
        void モールが異なれば注文番号が同じでも別物とみなす() {
            // モールごとに採番体系が独立しているため、注文番号だけでは一意にならない
            assertThat(new MallOrderKey(1L, "0001")).isNotEqualTo(new MallOrderKey(2L, "0001"));
        }
    }
}
