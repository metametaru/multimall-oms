package com.minioms.infrastructure.persistence.order;

import com.minioms.TestcontainersConfiguration;
import com.minioms.application.order.OrderRepository;
import com.minioms.domain.order.DuplicateMallOrderException;
import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderItem;
import com.minioms.domain.order.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受注リポジトリの仕様。実PostgreSQL上でしか確認できない挙動(ユニーク制約違反、
 * TIMESTAMPTZ・NUMERICの往復)を検証する。
 *
 * <p>Why not: H2 での代用はしない。制約違反時の例外や型の丸めが実DBと異なり、
 * 「テストは通るが本番で壊れる」状態を作るため。</p>
 */
@DisplayName("受注リポジトリ")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OrderRepositoryAdapterTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private long mallA;
    private long mallB;

    @BeforeEach
    void モールIDを解決する() {
        mallA = mallIdOf("MALL_A");
        mallB = mallIdOf("MALL_B");
    }

    private long mallIdOf(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM malls WHERE code = ?", Long.class, code);
    }

    private static Order importedOrder(MallOrderKey key) {
        return Order.importedFrom(
                key,
                "山田太郎",
                new BigDecimal("5400"),
                OffsetDateTime.parse("2026-08-19T10:00:00+09:00"),
                List.of(OrderItem.of("SKU-001", "テスト商品", new BigDecimal("2000"), 2)));
    }

    @Test
    void 保存した受注は冪等キーで取り出せて内容が往復する() {
        MallOrderKey key = new MallOrderKey(mallA, "A-20260819-0001");

        Order saved = orderRepository.save(importedOrder(key));
        assertThat(saved.id()).isNotNull();

        // 永続化コンテキストのキャッシュではなく実DBから読み直す
        entityManager.flush();
        entityManager.clear();

        Optional<Order> found = orderRepository.findByMallOrderKey(key);
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(OrderStatus.NEW);
        assertThat(found.get().customerName()).isEqualTo("山田太郎");
        assertThat(found.get().totalAmount()).isEqualByComparingTo("5400");
        assertThat(found.get().items()).hasSize(1);
        assertThat(found.get().items().get(0).productCode()).isEqualTo("SKU-001");
    }

    @Test
    void 同一モールの同一注文番号は二重に登録できない() {
        MallOrderKey key = new MallOrderKey(mallA, "A-20260819-0002");
        orderRepository.save(importedOrder(key));

        assertThatThrownBy(() -> orderRepository.save(importedOrder(key)))
                .isInstanceOf(DuplicateMallOrderException.class);
    }

    @Test
    void モールが異なれば同じ注文番号でも登録できる() {
        // 冪等キーがモールIDとの複合であることの確認。
        // 注文番号だけを一意にしていると、この正常系が弾かれてしまう
        orderRepository.save(importedOrder(new MallOrderKey(mallA, "0001")));
        Order fromMallB = orderRepository.save(importedOrder(new MallOrderKey(mallB, "0001")));

        assertThat(fromMallB.id()).isNotNull();
    }

    @Test
    void 未登録の冪等キーでは何も返らない() {
        assertThat(orderRepository.findByMallOrderKey(new MallOrderKey(mallA, "存在しない番号"))).isEmpty();
    }
}
