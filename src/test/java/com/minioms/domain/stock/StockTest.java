package com.minioms.domain.stock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 在庫の業務ルール。
 *
 * <p>在庫には「実在庫(倉庫にある数)」と「引当済(出荷が約束された数)」の2つの数がある。
 * 引当は倉庫からモノを出す操作ではないため実在庫を減らさない。
 * 出荷して初めて実在庫が減る。この区別が崩れると、売れる数を読み違えて
 * 欠品(引当できない注文を受ける)か過剰在庫(引当済を二重に売る)を起こす。</p>
 */
@DisplayName("在庫")
class StockTest {

    private static Stock 在庫(int 実在庫, int 引当済) {
        return new Stock(1L, "SKU-001", 実在庫, 引当済, 0L);
    }

    @Nested
    @DisplayName("引当可能数")
    class 引当可能数 {

        @Test
        void 引当可能数は実在庫から引当済を差し引いた数() {
            assertThat(在庫(10, 3).availableQuantity()).isEqualTo(7);
        }

        @Test
        void 全数が引当済なら引当可能数は0() {
            assertThat(在庫(10, 10).availableQuantity()).isZero();
        }
    }

    @Nested
    @DisplayName("引当")
    class 引当 {

        @Test
        void 引当は引当済を増やすが実在庫は減らさない() {
            // 引当は倉庫からモノを出す操作ではない。実在庫が減るのは出荷時
            Stock allocated = 在庫(10, 0).allocate(3);

            assertThat(allocated.quantityAllocated()).isEqualTo(3);
            assertThat(allocated.quantityOnHand()).isEqualTo(10);
            assertThat(allocated.availableQuantity()).isEqualTo(7);
        }

        @Test
        void 引当可能数ちょうどまでは引き当てられる() {
            assertThat(在庫(10, 4).allocate(6).availableQuantity()).isZero();
        }

        @Test
        void 引当可能数を超える引当はできない() {
            // 実在庫が残っていても、他の受注に約束済みの分は引き当てられない
            assertThatThrownBy(() -> 在庫(10, 8).allocate(3))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("SKU-001");
        }

        @Test
        void 引当しても元の在庫インスタンスは変化しない() {
            Stock original = 在庫(10, 0);
            original.allocate(3);

            assertThat(original.quantityAllocated()).isZero();
        }

        @Test
        void 数量が0以下の引当はできない() {
            assertThatThrownBy(() -> 在庫(10, 0).allocate(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("引当解除")
    class 引当解除 {

        @Test
        void 引当解除は引当済を減らし実在庫は変えない() {
            // キャンセル時に使う。モノは動いていないので実在庫は変わらない
            Stock released = 在庫(10, 5).release(3);

            assertThat(released.quantityAllocated()).isEqualTo(2);
            assertThat(released.quantityOnHand()).isEqualTo(10);
        }

        @Test
        void 引当済を超える解除は在庫の数え方が壊れているため許さない() {
            assertThatThrownBy(() -> 在庫(10, 2).release(3))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("出荷")
    class 出荷 {

        @Test
        void 出荷は実在庫と引当済の両方を減らす() {
            // 引当済のモノが倉庫から出ていく。約束(引当)も同時に果たされる
            Stock shipped = 在庫(10, 5).shipOut(5);

            assertThat(shipped.quantityOnHand()).isEqualTo(5);
            assertThat(shipped.quantityAllocated()).isZero();
            assertThat(shipped.availableQuantity()).isEqualTo(5);
        }

        @Test
        void 引当済を超える出荷は許さない() {
            // 引当していない在庫が勝手に出ていくと、他の受注への約束が守れなくなる
            assertThatThrownBy(() -> 在庫(10, 2).shipOut(3))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("成立しない在庫")
    class 成立しない在庫 {

        @Test
        void 引当済が実在庫を超える状態は作れない() {
            assertThatThrownBy(() -> 在庫(3, 5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 実在庫と引当済は負にできない() {
            assertThatThrownBy(() -> 在庫(-1, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> 在庫(10, -1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 商品コードのない在庫は作れない() {
            assertThatThrownBy(() -> new Stock(1L, " ", 10, 0, 0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
