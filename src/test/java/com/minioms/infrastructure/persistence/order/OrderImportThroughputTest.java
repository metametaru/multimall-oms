package com.minioms.infrastructure.persistence.order;

import com.minioms.TestcontainersConfiguration;
import com.minioms.application.order.ImportOrdersUseCase;
import com.minioms.application.order.ImportSummary;
import com.minioms.application.order.MallOrderClient;
import com.minioms.application.order.OrderRepository;
import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受注取込のスループットを計測する。
 *
 * <p>結果は README に記録する。合否ではなく数値を得ることが目的であり、
 * 実行時間の閾値をアサートしない。閾値を置くと、実行環境の差でCIが赤くなり、
 * 「落ちても気にしないテスト」が1本増えるだけになる。</p>
 *
 * <p>Why not: 通常の {@code test} からは除外している({@code @Tag("perf")})。
 * 1万件の保存に数十秒かかり、毎回のCIに載せると、得られる情報の量に対して
 * 待ち時間が見合わない。計測したいときに {@code ./gradlew perfTest} で回す。</p>
 *
 * <p>この計測に含まれないもの: モールAPIのHTTP応答時間、XML/JSONのパース。
 * 取込の律速はDB書き込みと冪等キーの制約チェックであり、そこだけを測っている。</p>
 */
@DisplayName("受注取込のスループット")
@Tag("perf")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class OrderImportThroughputTest {

    private static final int ORDER_COUNT = 10_000;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 受注1万件の取込と同じ1万件の再取込にかかる時間を測る() {
        long mallId = jdbcTemplate.queryForObject(
                "SELECT id FROM malls WHERE code = 'MALL_A'", Long.class);
        List<Order> orders = IntStream.rangeClosed(1, ORDER_COUNT)
                .mapToObj(i -> order(mallId, i))
                .toList();

        ImportOrdersUseCase useCase =
                new ImportOrdersUseCase(List.of(new FixedMallClient(mallId, orders)), orderRepository);

        long startFirst = System.nanoTime();
        ImportSummary first = useCase.execute(OffsetDateTime.now().minusHours(1));
        long firstMillis = (System.nanoTime() - startFirst) / 1_000_000;

        // 取込ウィンドウを重ねる運用では、2回目以降は大半が冪等キーで弾かれる。
        // 実運用で常態的に走るのはこちらであり、こちらの方が重要
        long startSecond = System.nanoTime();
        ImportSummary second = useCase.execute(OffsetDateTime.now().minusHours(1));
        long secondMillis = (System.nanoTime() - startSecond) / 1_000_000;

        System.out.printf(
                "%n[取込スループット] 新規 %d件: %,d ms (%,.0f 件/秒) / 再取込(全件スキップ) %d件: %,d ms (%,.0f 件/秒)%n",
                first.totalImported(), firstMillis, rate(first.totalImported(), firstMillis),
                second.totalSkipped(), secondMillis, rate(second.totalSkipped(), secondMillis));

        assertThat(first.totalImported()).isEqualTo(ORDER_COUNT);
        assertThat(second.totalSkipped()).isEqualTo(ORDER_COUNT);
        assertThat(second.totalImported()).isZero();
    }

    private static double rate(int count, long millis) {
        return millis == 0 ? 0 : count * 1000.0 / millis;
    }

    private Order order(long mallId, int index) {
        return Order.importedFrom(
                new MallOrderKey(mallId, "THROUGHPUT-%06d".formatted(index)),
                "顧客%d".formatted(index),
                BigDecimal.valueOf(1000 + index % 9000),
                OffsetDateTime.now().minusMinutes(index),
                List.of(new OrderItem(null, "SKU-001", "計測用商品", BigDecimal.valueOf(500), 1 + index % 3)));
    }

    /** 固定の受注リストを返すだけのクライアント。HTTPとパースを計測から外すために使う */
    private record FixedMallClient(long mallId, List<Order> orders) implements MallOrderClient {

        @Override
        public long mallId() {
            return mallId;
        }

        @Override
        public List<Order> fetchOrdersPlacedSince(OffsetDateTime since) {
            return orders;
        }
    }
}
