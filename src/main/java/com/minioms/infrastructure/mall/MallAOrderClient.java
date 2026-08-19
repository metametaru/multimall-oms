package com.minioms.infrastructure.mall;

import com.minioms.application.order.MallOrderClient;
import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * モールA(JSON形式)からの受注取得。
 *
 * <p>モールAは形式が素直なため、変換はフィールドの詰め替えで済む。
 * モールごとの複雑さの差は {@link MallBOrderClient} と比較すると分かりやすい。</p>
 */
class MallAOrderClient implements MallOrderClient {

    static final String MALL_CODE = "MALL_A";

    private static final Logger log = LoggerFactory.getLogger(MallAOrderClient.class);

    private final RestClient restClient;
    private final MallCatalog mallCatalog;

    MallAOrderClient(RestClient restClient, MallCatalog mallCatalog) {
        this.restClient = restClient;
        this.mallCatalog = mallCatalog;
    }

    @Override
    public long mallId() {
        return mallCatalog.idOf(MALL_CODE);
    }

    @Override
    public List<Order> fetchOrdersPlacedSince(OffsetDateTime since) {
        MallAOrderResponse response = restClient.get()
                .uri(uri -> uri.path("/orders")
                        .queryParam("since", since.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .build())
                .retrieve()
                .body(MallAOrderResponse.class);

        if (response == null || response.orders() == null) {
            log.warn("モールAのレスポンスが空でした");
            return List.of();
        }
        return response.orders().stream().map(this::toOrder).toList();
    }

    private Order toOrder(MallAOrderResponse.MallAOrder source) {
        List<OrderItem> items = source.items().stream()
                .map(item -> OrderItem.of(item.sku(), item.name(), item.price(), item.qty()))
                .toList();

        return Order.importedFrom(
                new MallOrderKey(mallId(), source.orderNo()),
                source.customerName(),
                source.totalAmount(),
                source.orderedAt(),
                items);
    }
}
