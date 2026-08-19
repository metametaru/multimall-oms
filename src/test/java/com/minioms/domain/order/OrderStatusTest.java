package com.minioms.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.minioms.domain.order.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受注ステータス遷移の仕様。
 * このテストを読めば、受注ライフサイクルの業務ルールが分かる状態を保つこと。
 */
@DisplayName("受注ステータスの状態遷移")
class OrderStatusTest {

    @Nested
    @DisplayName("正常な業務フロー")
    class 正常な業務フロー {

        @Test
        void 新規受注は確認を経て出荷指示_出荷完了まで進む() {
            assertThat(NEW.canTransitionTo(CONFIRMED)).isTrue();
            assertThat(CONFIRMED.canTransitionTo(SHIPPING_INSTRUCTED)).isTrue();
            assertThat(SHIPPING_INSTRUCTED.canTransitionTo(SHIPPED)).isTrue();
        }

        @Test
        void 出荷完了後は返品を受け付けられる() {
            assertThat(SHIPPED.canTransitionTo(RETURNED)).isTrue();
        }
    }

    @Nested
    @DisplayName("キャンセル可否のルール")
    class キャンセル可否のルール {

        @Test
        void 出荷指示前ならキャンセルできる() {
            assertThat(NEW.canTransitionTo(CANCELLED)).isTrue();
            assertThat(CONFIRMED.canTransitionTo(CANCELLED)).isTrue();
        }

        @Test
        void 出荷指示済みの受注はキャンセルできない() {
            // 倉庫で既にピッキングが進んでいる可能性があるため、
            // 出荷指示後のキャンセルはシステム上許可しない
            assertThat(SHIPPING_INSTRUCTED.canTransitionTo(CANCELLED)).isFalse();
        }

        @Test
        void 出荷完了後の受注はキャンセルではなく返品として扱う() {
            assertThat(SHIPPED.canTransitionTo(CANCELLED)).isFalse();
            assertThat(SHIPPED.canTransitionTo(RETURNED)).isTrue();
        }
    }

    @Nested
    @DisplayName("在庫引当を抱える期間")
    class 在庫引当を抱える期間 {

        @ParameterizedTest(name = "{0} は在庫を引き当てている")
        @CsvSource({"CONFIRMED", "SHIPPING_INSTRUCTED"})
        void 確認済から出荷指示済までは在庫を引き当てている(OrderStatus status) {
            // 引当は確認時に行う。取込直後(NEW)は内容確認前で、モール側キャンセルや
            // 不正注文も混じるため、未確認の受注で在庫を押さえない
            assertThat(status.holdsStockAllocation()).isTrue();
        }

        @ParameterizedTest(name = "{0} は在庫を引き当てていない")
        @CsvSource({"NEW", "SHIPPED", "CANCELLED", "RETURNED"})
        void 確認前と出荷後は在庫を引き当てていない(OrderStatus status) {
            // 出荷完了後は引当ではなく実在庫が減っている。
            // ここを引当済のままにすると、出荷した数を二重に確保し続けることになる
            assertThat(status.holdsStockAllocation()).isFalse();
        }
    }

    @Nested
    @DisplayName("終端ステータス")
    class 終端ステータス {

        @Test
        void キャンセルと返品は終端であり以降の遷移は存在しない() {
            assertThat(CANCELLED.isTerminal()).isTrue();
            assertThat(RETURNED.isTerminal()).isTrue();
        }
    }

    @Nested
    @DisplayName("不正な遷移の防止")
    class 不正な遷移の防止 {

        @ParameterizedTest(name = "{0} → {1} は許可されない")
        @CsvSource({
                "NEW, SHIPPED",                  // 確認・出荷指示の飛ばし
                "NEW, SHIPPING_INSTRUCTED",      // 確認の飛ばし
                "CONFIRMED, SHIPPED",            // 出荷指示の飛ばし
                "CONFIRMED, RETURNED",           // 未出荷での返品
                "SHIPPED, CONFIRMED",            // 逆行
                "CANCELLED, CONFIRMED",          // 終端からの復帰
                "RETURNED, SHIPPED",             // 終端からの復帰
        })
        void 工程の飛ばし_逆行_終端からの復帰は例外になる(OrderStatus from, OrderStatus to) {
            assertThatThrownBy(() -> from.transitionTo(to))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }
}
