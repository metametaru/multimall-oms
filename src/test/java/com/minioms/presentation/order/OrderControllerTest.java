package com.minioms.presentation.order;

import com.minioms.application.mall.MallDirectory;
import com.minioms.application.order.FindOrdersUseCase;
import com.minioms.application.order.OrderLifecycleUseCase;
import com.minioms.application.order.OrderSearchCriteria;
import com.minioms.application.order.OrderSearchResult;
import com.minioms.application.order.OrderSummary;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 受注APIのHTTP契約。
 *
 * <p>オペレーターは一覧で受注を選び、詳細を確認し、出荷指示やキャンセルを行う。
 * ここで固定するのは業務ルールそのものではなく、業務ルール違反や不正な入力が
 * どのHTTPステータスで表現されるかという契約。</p>
 *
 * <p>Why not: 認証は掛けずにテストする。第3週の認証実装まで SecurityConfig は
 * 全開放であり、ここに認証を含めると本実装への差し替え時にAPI契約のテストまで
 * 巻き添えで壊れるため。</p>
 */
@DisplayName("受注API")
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    private static final long MALL_A_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FindOrdersUseCase findOrdersUseCase;

    @MockitoBean
    private OrderLifecycleUseCase orderLifecycleUseCase;

    @MockitoBean
    private MallDirectory mallDirectory;

    private static OrderSummary summary(long id, OrderStatus status) {
        return new OrderSummary(id, MALL_A_ID, "A-20260819-0001", status, "山田太郎",
                new BigDecimal("5400"), OffsetDateTime.parse("2026-08-19T10:00:00+09:00"), 0L);
    }

    private static Order order(OrderStatus status) {
        return new Order(10L, new MallOrderKey(MALL_A_ID, "A-20260819-0001"), status, "山田太郎",
                new BigDecimal("5400"), OffsetDateTime.parse("2026-08-19T10:00:00+09:00"),
                List.of(new OrderItem(100L, "SKU-001", "ワイヤレスイヤホン", new BigDecimal("2500"), 2)), 3L);
    }

    @Nested
    @DisplayName("受注一覧")
    class 受注一覧 {

        @Test
        void 受注の一覧と総件数を返す() throws Exception {
            given(findOrdersUseCase.search(any())).willReturn(new OrderSearchResult(
                    List.of(summary(10L, OrderStatus.NEW), summary(11L, OrderStatus.CONFIRMED)), 42L));
            given(mallDirectory.codeOf(MALL_A_ID)).willReturn(Optional.of("MALL_A"));

            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orders.length()").value(2))
                    .andExpect(jsonPath("$.totalCount").value(42))
                    .andExpect(jsonPath("$.orders[0].id").value(10))
                    .andExpect(jsonPath("$.orders[0].status").value("NEW"))
                    .andExpect(jsonPath("$.orders[0].customerName").value("山田太郎"));
        }

        @Test
        void モールはDB採番のIDではなくモールコードで表現される() throws Exception {
            // mallId はDBが採番する内部識別子で、環境ごとに値が変わりうる。
            // API契約を環境から独立させるため、外部にはコードだけを見せる
            given(findOrdersUseCase.search(any())).willReturn(
                    new OrderSearchResult(List.of(summary(10L, OrderStatus.NEW)), 1L));
            given(mallDirectory.codeOf(MALL_A_ID)).willReturn(Optional.of("MALL_A"));

            mockMvc.perform(get("/api/orders"))
                    .andExpect(jsonPath("$.orders[0].mallCode").value("MALL_A"))
                    .andExpect(jsonPath("$.orders[0].mallId").doesNotExist());
        }

        @Test
        void ステータスとモールで絞り込める() throws Exception {
            given(findOrdersUseCase.search(any())).willReturn(new OrderSearchResult(List.of(), 0L));
            given(mallDirectory.idOf("MALL_A")).willReturn(Optional.of(MALL_A_ID));

            mockMvc.perform(get("/api/orders").param("status", "NEW").param("mallCode", "MALL_A"))
                    .andExpect(status().isOk());

            ArgumentCaptor<OrderSearchCriteria> criteria = ArgumentCaptor.forClass(OrderSearchCriteria.class);
            verify(findOrdersUseCase).search(criteria.capture());
            assertThat(criteria.getValue().status()).isEqualTo(OrderStatus.NEW);
            assertThat(criteria.getValue().mallId()).isEqualTo(MALL_A_ID);
        }

        @Test
        void 絞り込みを指定しなければ全件が対象になる() throws Exception {
            given(findOrdersUseCase.search(any())).willReturn(new OrderSearchResult(List.of(), 0L));

            mockMvc.perform(get("/api/orders")).andExpect(status().isOk());

            ArgumentCaptor<OrderSearchCriteria> criteria = ArgumentCaptor.forClass(OrderSearchCriteria.class);
            verify(findOrdersUseCase).search(criteria.capture());
            assertThat(criteria.getValue().status()).isNull();
            assertThat(criteria.getValue().mallId()).isNull();
        }

        @Test
        void 存在しないステータスを指定すると400になる() throws Exception {
            mockMvc.perform(get("/api/orders").param("status", "UNKNOWN_STATUS"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("リクエストが不正です"));
        }

        @Test
        void 存在しないモールコードを指定すると400になる() throws Exception {
            // 未知のモールでの検索を「該当0件」で返すと、絞り込みの打ち間違いに
            // 気づけない。入力の誤りとして明示的に返す
            given(mallDirectory.idOf("MALL_Z")).willReturn(Optional.empty());

            mockMvc.perform(get("/api/orders").param("mallCode", "MALL_Z"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("存在しないモールコードです: MALL_Z"));
        }
    }

    @Nested
    @DisplayName("受注詳細")
    class 受注詳細 {

        @Test
        void 明細を含む受注を返す() throws Exception {
            given(findOrdersUseCase.findById(10L)).willReturn(order(OrderStatus.NEW));
            given(mallDirectory.codeOf(MALL_A_ID)).willReturn(Optional.of("MALL_A"));

            mockMvc.perform(get("/api/orders/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.mallOrderNumber").value("A-20260819-0001"))
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].productCode").value("SKU-001"))
                    .andExpect(jsonPath("$.items[0].subtotal").value(5000));
        }

        @Test
        void 楽観ロック用のバージョンを返す() throws Exception {
            // 更新系APIはこのバージョンを送り返してもらうことで、一覧を開いたまま
            // 他のオペレーターが進めた受注を上書きする事故を防ぐ
            given(findOrdersUseCase.findById(10L)).willReturn(order(OrderStatus.NEW));
            given(mallDirectory.codeOf(anyLong())).willReturn(Optional.of("MALL_A"));

            mockMvc.perform(get("/api/orders/10"))
                    .andExpect(jsonPath("$.version").value(3));
        }

        @Test
        void 存在しない受注は404になる() throws Exception {
            given(findOrdersUseCase.findById(999L)).willThrow(new OrderNotFoundException(999L));

            mockMvc.perform(get("/api/orders/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("受注が見つかりません"))
                    .andExpect(jsonPath("$.detail").value("受注が存在しません: id=999"));
        }
    }

    @Nested
    @DisplayName("受注を業務フローに沿って進める")
    class 受注を業務フローに沿って進める {

        private static final String VERSION_3 = """
                {"version": 3}
                """;

        @Test
        void 出荷指示は業務操作としてのURLで受け付ける() throws Exception {
            // Why not: PATCH でstatusフィールドを書き換える形にしない。
            // クライアントが状態機械を知っている前提になり、操作の業務的な意味が消える
            given(orderLifecycleUseCase.instructShipping(10L, 3L))
                    .willReturn(order(OrderStatus.SHIPPING_INSTRUCTED));
            given(mallDirectory.codeOf(anyLong())).willReturn(Optional.of("MALL_A"));

            mockMvc.perform(post("/api/orders/10/shipping-instruction")
                            .contentType(APPLICATION_JSON).content(VERSION_3))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SHIPPING_INSTRUCTED"));
        }

        @Test
        void キャンセルは業務操作としてのURLで受け付ける() throws Exception {
            given(orderLifecycleUseCase.cancel(10L, 3L)).willReturn(order(OrderStatus.CANCELLED));
            given(mallDirectory.codeOf(anyLong())).willReturn(Optional.of("MALL_A"));

            mockMvc.perform(post("/api/orders/10/cancellation")
                            .contentType(APPLICATION_JSON).content(VERSION_3))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        void バージョンを省略したリクエストは400になる() throws Exception {
            // 省略を許すと「今のDBの状態に無条件で適用する」意味になり、
            // 楽観ロックが機能しなくなる
            mockMvc.perform(post("/api/orders/10/cancellation")
                            .contentType(APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("リクエストが不正です"))
                    .andExpect(jsonPath("$.detail").value("version: version は必須です"));
        }

        @Test
        void 許可されていない遷移は409になる() throws Exception {
            // リクエストの形式は正しく、受注の「今の状態」と衝突しているだけなので400にはしない
            given(orderLifecycleUseCase.cancel(10L, 3L)).willThrow(
                    new InvalidStatusTransitionException(OrderStatus.SHIPPING_INSTRUCTED, OrderStatus.CANCELLED));

            mockMvc.perform(post("/api/orders/10/cancellation")
                            .contentType(APPLICATION_JSON).content(VERSION_3))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("処理できない要求です"))
                    .andExpect(jsonPath("$.detail").value("不正なステータス遷移: SHIPPING_INSTRUCTED → CANCELLED"));
        }

        @Test
        void 他のオペレーターが先に更新していると409になる() throws Exception {
            given(orderLifecycleUseCase.confirm(10L, 3L)).willThrow(new ConcurrentOrderUpdateException(10L));

            mockMvc.perform(post("/api/orders/10/confirmation")
                            .contentType(APPLICATION_JSON).content(VERSION_3))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("受注が他の処理によって更新されています: id=10"));
        }

        @Test
        void 存在しない受注への操作は404になる() throws Exception {
            given(orderLifecycleUseCase.ship(999L, 3L)).willThrow(new OrderNotFoundException(999L));

            mockMvc.perform(post("/api/orders/999/shipment")
                            .contentType(APPLICATION_JSON).content(VERSION_3))
                    .andExpect(status().isNotFound());
        }
    }
}
