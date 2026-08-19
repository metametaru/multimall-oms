package com.minioms.infrastructure.persistence.stock;

import com.minioms.TestcontainersConfiguration;
import com.minioms.application.order.OrderLifecycleUseCase;
import com.minioms.application.order.OrderRepository;
import com.minioms.application.stock.StockRepository;
import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderItem;
import com.minioms.domain.order.OrderStatus;
import com.minioms.domain.stock.InsufficientStockException;
import com.minioms.domain.stock.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受注のステータス更新と在庫引当が一体で成立することの仕様。
 *
 * <p>「確認済だが在庫は押さえられていない」「引当済だが受注は未確認」という状態は、
 * 実在庫と帳簿の食い違いに直結し、欠品や二重売りとして表面化する。
 * この一体性はトランザクションでしか保証できないため、実DBに対して確認する。</p>
 *
 * <p>Why not: クラスに {@code @Transactional} を付けない。テストのトランザクションで
 * 全体を包むと、ユースケース側のロールバックが起きたかどうかを観測できなくなる。
 * 代わりに各テストの前にテーブルを掃除する。</p>
 */
@DisplayName("受注操作と在庫引当の一体性")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class OrderLifecycleIntegrationTest {

    private static final String SKU = "SKU-統合テスト";

    @Autowired
    private OrderLifecycleUseCase orderLifecycleUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long mallA;

    @BeforeEach
    void 前回のテストの痕跡を消す() {
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM stocks WHERE product_code = ?", SKU);
        mallA = jdbcTemplate.queryForObject("SELECT id FROM malls WHERE code = ?", Long.class, "MALL_A");
    }

    private Order 受注を用意する(int 数量) {
        return orderRepository.save(Order.importedFrom(
                new MallOrderKey(mallA, "A-統合テスト-001"),
                "山田太郎",
                new BigDecimal("5400"),
                OffsetDateTime.parse("2026-08-19T10:00:00+09:00"),
                List.of(OrderItem.of(SKU, "テスト商品", new BigDecimal("2700"), 数量))));
    }

    private void 在庫を用意する(int 実在庫) {
        stockRepository.save(Stock.of(SKU, 実在庫));
    }

    private Stock 在庫() {
        return stockRepository.findByProductCode(SKU).orElseThrow();
    }

    private OrderStatus 受注のステータス(long orderId) {
        return orderRepository.findById(orderId).orElseThrow().status();
    }

    @Test
    void 確認すると在庫が引き当てられる() {
        在庫を用意する(10);
        Order order = 受注を用意する(3);

        orderLifecycleUseCase.confirm(order.id(), order.version());

        assertThat(在庫().quantityAllocated()).isEqualTo(3);
        assertThat(在庫().quantityOnHand()).isEqualTo(10);
        assertThat(受注のステータス(order.id())).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void 在庫が足りなければ受注のステータスも進まない() {
        // 在庫の引当に失敗したら受注の更新ごと巻き戻る。
        // ここが分かれると「確認済だが在庫が無い受注」が生まれ、出荷時に破綻する
        在庫を用意する(2);
        Order order = 受注を用意する(3);

        assertThatThrownBy(() -> orderLifecycleUseCase.confirm(order.id(), order.version()))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(受注のステータス(order.id())).isEqualTo(OrderStatus.NEW);
        assertThat(在庫().quantityAllocated()).isZero();
    }

    @Test
    void 出荷まで進めると引当が実在庫から落ちる() {
        在庫を用意する(10);
        Order order = 受注を用意する(3);

        Order confirmed = orderLifecycleUseCase.confirm(order.id(), order.version());
        Order instructed = orderLifecycleUseCase.instructShipping(confirmed.id(), confirmed.version());
        orderLifecycleUseCase.ship(instructed.id(), instructed.version());

        assertThat(在庫().quantityOnHand()).isEqualTo(7);
        assertThat(在庫().quantityAllocated()).isZero();
    }

    @Test
    void 確認済の受注をキャンセルすると引当が戻る() {
        在庫を用意する(10);
        Order order = 受注を用意する(3);

        Order confirmed = orderLifecycleUseCase.confirm(order.id(), order.version());
        orderLifecycleUseCase.cancel(confirmed.id(), confirmed.version());

        assertThat(在庫().quantityAllocated()).isZero();
        assertThat(在庫().quantityOnHand()).isEqualTo(10);
        assertThat(在庫().availableQuantity()).isEqualTo(10);
    }

    @Test
    void 引当済の分は他の受注に引き当てられない() {
        // 実在庫が残っていても、他の受注に約束済みの分は使えない
        在庫を用意する(5);
        Order first = 受注を用意する(4);
        orderLifecycleUseCase.confirm(first.id(), first.version());

        Order second = orderRepository.save(Order.importedFrom(
                new MallOrderKey(mallA, "A-統合テスト-002"),
                "鈴木一郎",
                new BigDecimal("5400"),
                OffsetDateTime.parse("2026-08-19T11:00:00+09:00"),
                List.of(OrderItem.of(SKU, "テスト商品", new BigDecimal("2700"), 2))));

        assertThatThrownBy(() -> orderLifecycleUseCase.confirm(second.id(), second.version()))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(在庫().quantityAllocated()).isEqualTo(4);
    }
}
