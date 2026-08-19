package com.minioms.infrastructure.mall;

import com.minioms.TestcontainersConfiguration;
import com.minioms.domain.order.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * モールAPIクライアントの仕様。モックモールAPIに実際にHTTPで接続し、
 * モール固有の形式がドメインの受注に変換されるところまでを確認する。
 *
 * <p>形式の差異(JSON / XML、ISO-8601 / 独自日時、ステータスコードの有無)を
 * クライアントが吸収しきることが、このシステムの中核的な価値になる。</p>
 */
@DisplayName("モールAPIクライアント")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "mock"})
@Import(TestcontainersConfiguration.class)
class MallOrderClientIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MallCatalog mallCatalog;

    /** モックは絞り込みをしないため、渡す値は結果に影響しない */
    private static final OffsetDateTime SINCE = OffsetDateTime.parse("2026-08-01T00:00:00+09:00");

    private String mockUrl(String mallPath) {
        return "http://localhost:%d/mock/%s".formatted(port, mallPath);
    }

    @Nested
    @DisplayName("モールA(JSON形式)")
    class モールA {

        private List<Order> fetch() {
            return new MallAOrderClient(
                    MallClientConfig.mallARestClient(mockUrl("mall-a")), mallCatalog)
                    .fetchOrdersPlacedSince(SINCE);
        }

        @Test
        void JSONレスポンスを受注に変換する() {
            List<Order> orders = fetch();

            assertThat(orders).hasSize(2);
            Order first = orders.get(0);
            assertThat(first.mallOrderKey().mallOrderNumber()).isEqualTo("A-20260819-0001");
            assertThat(first.customerName()).isEqualTo("山田太郎");
            assertThat(first.totalAmount()).isEqualByComparingTo("5400");
            assertThat(first.orderedAt()).isEqualTo(OffsetDateTime.parse("2026-08-19T10:00:00+09:00"));
        }

        @Test
        void 明細も変換される() {
            assertThat(fetch().get(0).items())
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.productCode()).isEqualTo("SKU-001");
                        assertThat(item.quantity()).isEqualTo(2);
                        assertThat(item.unitPrice()).isEqualByComparingTo("2500");
                    });
        }

        @Test
        void モールAのIDが冪等キーに設定される() {
            assertThat(fetch())
                    .allSatisfy(order -> assertThat(order.mallOrderKey().mallId())
                            .isEqualTo(mallCatalog.requireIdOf(MallAOrderClient.MALL_CODE)));
        }
    }

    @Nested
    @DisplayName("モールB(XML + 独自ステータスコード)")
    class モールB {

        private List<Order> fetch() {
            return new MallBOrderClient(
                    MallClientConfig.mallBRestClient(mockUrl("mall-b")), mallCatalog)
                    .fetchOrdersPlacedSince(SINCE);
        }

        @Test
        void XMLレスポンスを受注に変換する() {
            assertThat(fetch())
                    .singleElement()
                    .satisfies(order -> {
                        assertThat(order.mallOrderKey().mallOrderNumber()).isEqualTo("B0000123");
                        assertThat(order.customerName()).isEqualTo("佐藤花子");
                        assertThat(order.totalAmount()).isEqualByComparingTo("3200");
                    });
        }

        @Test
        void タイムゾーンなしの独自日時フォーマットをJSTとして解釈する() {
            // モールBは 20260819103000 のようにTZ情報を持たない形式で返す
            assertThat(fetch().get(0).orderedAt())
                    .isEqualTo(OffsetDateTime.parse("2026-08-19T10:30:00+09:00"));
        }

        @Test
        void モール側で発送済みの受注は取り込まない() {
            // B0000124 は statusCode=03(発送済)。OMSの状態機械の起点はNEWであり、
            // 発送済みを取り込むと実態と食い違う
            assertThat(fetch())
                    .extracting(order -> order.mallOrderKey().mallOrderNumber())
                    .doesNotContain("B0000124");
        }

        @Test
        void 未知のステータスコードの受注は他の受注を巻き添えにせず除外される() {
            // B0000125 は statusCode=77(未知)。除外はされるが、
            // 正常な B0000123 の取込は成功している
            assertThat(fetch())
                    .extracting(order -> order.mallOrderKey().mallOrderNumber())
                    .containsExactly("B0000123");
        }
    }
}
