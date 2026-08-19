package com.minioms.infrastructure.persistence.order;

import com.minioms.TestcontainersConfiguration;
import com.minioms.application.order.OrderRepository;
import com.minioms.application.order.OrderSearchCriteria;
import com.minioms.application.order.OrderSearchQuery;
import com.minioms.application.order.OrderSearchResult;
import com.minioms.application.order.OrderSummary;
import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderItem;
import com.minioms.domain.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受注検索の仕様。オペレーターの主画面(受注一覧)を支えるクエリであり、
 * 「未確認の受注を古い順に処理する」という運用がそのまま並び順の仕様になる。
 *
 * <p>Why not: 一覧でも集約({@code Order})を返す案は採らない。明細を必ず伴う集約を
 * 一覧の件数分だけ復元すると、画面に出さない明細のロードが件数分走る。
 * 一覧専用の読み取りモデル {@link OrderSummary} を返すことでこれを避ける。</p>
 */
@DisplayName("受注検索")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class OrderSearchQueryAdapterTest {

    @Autowired
    private OrderSearchQuery orderSearchQuery;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private Order 受注を作る(long mallId, String mallOrderNumber, String orderedAt) {
        return orderRepository.save(Order.importedFrom(
                new MallOrderKey(mallId, mallOrderNumber),
                "山田太郎",
                new BigDecimal("5400"),
                OffsetDateTime.parse(orderedAt),
                List.of(OrderItem.of("SKU-001", "テスト商品", new BigDecimal("2700"), 2))));
    }

    private Order 確認済の受注を作る(long mallId, String mallOrderNumber, String orderedAt) {
        return orderRepository.save(受注を作る(mallId, mallOrderNumber, orderedAt).confirm());
    }

    private OrderSearchResult 検索(OrderStatus status, Long mallId) {
        return orderSearchQuery.search(new OrderSearchCriteria(status, mallId, 0, 20));
    }

    private static List<String> 注文番号(OrderSearchResult result) {
        return result.orders().stream().map(OrderSummary::mallOrderNumber).toList();
    }

    @Test
    void 絞り込みを指定しなければ全件が対象になる() {
        受注を作る(mallA, "A-001", "2026-08-19T10:00:00+09:00");
        受注を作る(mallB, "B-001", "2026-08-19T11:00:00+09:00");

        assertThat(検索(null, null).totalCount()).isEqualTo(2);
    }

    @Test
    void ステータスで絞り込める() {
        受注を作る(mallA, "A-001", "2026-08-19T10:00:00+09:00");
        確認済の受注を作る(mallA, "A-002", "2026-08-19T11:00:00+09:00");

        assertThat(注文番号(検索(OrderStatus.NEW, null))).containsExactly("A-001");
        assertThat(注文番号(検索(OrderStatus.CONFIRMED, null))).containsExactly("A-002");
    }

    @Test
    void モールで絞り込める() {
        受注を作る(mallA, "A-001", "2026-08-19T10:00:00+09:00");
        受注を作る(mallB, "B-001", "2026-08-19T11:00:00+09:00");

        assertThat(注文番号(検索(null, mallA))).containsExactly("A-001");
    }

    @Test
    void ステータスとモールは同時に絞り込める() {
        受注を作る(mallA, "A-001", "2026-08-19T10:00:00+09:00");
        確認済の受注を作る(mallA, "A-002", "2026-08-19T11:00:00+09:00");
        受注を作る(mallB, "B-001", "2026-08-19T12:00:00+09:00");

        assertThat(注文番号(検索(OrderStatus.NEW, mallA))).containsExactly("A-001");
    }

    @Test
    void 注文日時の古い順に並ぶ() {
        // 「未確認の受注を古い順に処理する」という運用に合わせる。
        // idx_orders_status_ordered_at もこの並び順を前提に張っている
        受注を作る(mallA, "A-新しい", "2026-08-19T15:00:00+09:00");
        受注を作る(mallA, "A-古い", "2026-08-19T09:00:00+09:00");
        受注を作る(mallA, "A-中間", "2026-08-19T12:00:00+09:00");

        assertThat(注文番号(検索(null, null))).containsExactly("A-古い", "A-中間", "A-新しい");
    }

    @Test
    void ページ内の件数は絞られても総件数は全体を返す() {
        // 画面のページャは「全何件中の何件目か」を出す。ページ内の件数では代用できない
        受注を作る(mallA, "A-001", "2026-08-19T10:00:00+09:00");
        受注を作る(mallA, "A-002", "2026-08-19T11:00:00+09:00");
        受注を作る(mallA, "A-003", "2026-08-19T12:00:00+09:00");

        OrderSearchResult firstPage = orderSearchQuery.search(new OrderSearchCriteria(null, null, 0, 2));
        assertThat(注文番号(firstPage)).containsExactly("A-001", "A-002");
        assertThat(firstPage.totalCount()).isEqualTo(3);

        OrderSearchResult secondPage = orderSearchQuery.search(new OrderSearchCriteria(null, null, 1, 2));
        assertThat(注文番号(secondPage)).containsExactly("A-003");
        assertThat(secondPage.totalCount()).isEqualTo(3);
    }

    @Test
    void 一覧の項目は明細を含まない() {
        // 一覧に出すのは受注単位の情報だけ。明細は詳細画面で初めて必要になる
        受注を作る(mallA, "A-001", "2026-08-19T10:00:00+09:00");

        OrderSummary summary = 検索(null, null).orders().get(0);
        assertThat(summary.customerName()).isEqualTo("山田太郎");
        assertThat(summary.totalAmount()).isEqualByComparingTo("5400");
        assertThat(summary.status()).isEqualTo(OrderStatus.NEW);
    }
}
