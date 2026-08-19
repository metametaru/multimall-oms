package com.minioms.infrastructure.mall;

import com.minioms.application.order.MallOrderClient;
import com.minioms.domain.order.MallOrderKey;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * モールB(XML + 独自ステータスコード)からの受注取得。腐敗防止層の本体。
 *
 * <p>モールBはXML、タイムゾーンなしの独自日時フォーマット、独自ステータスコードと
 * OMSの語彙から遠い。これらの差異をすべてここで吸収し、上位層には
 * ドメインの {@link Order} だけを渡す。</p>
 *
 * <p>Why not: 変換できない受注で例外を投げない。モール側の仕様変更や1件の
 * 不正データで、同一バッチの正常な受注まで取り込めなくなるため、
 * 該当分だけを除外してログに残す。</p>
 */
class MallBOrderClient implements MallOrderClient {

    static final String MALL_CODE = "MALL_B";

    private static final Logger log = LoggerFactory.getLogger(MallBOrderClient.class);

    /** モールBは日時をタイムゾーンなしで返す。仕様上JST固定 */
    private static final DateTimeFormatter MALL_B_DATETIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
    private static final ZoneId MALL_B_ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter SINCE_FORMAT = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");

    private final RestClient restClient;
    private final MallCatalog mallCatalog;

    MallBOrderClient(RestClient restClient, MallCatalog mallCatalog) {
        this.restClient = restClient;
        this.mallCatalog = mallCatalog;
    }

    @Override
    public long mallId() {
        return mallCatalog.idOf(MALL_CODE);
    }

    @Override
    public List<Order> fetchOrdersPlacedSince(OffsetDateTime since) {
        MallBOrderResponse response = restClient.get()
                .uri(uri -> uri.path("/orderList")
                        .queryParam("from", since.atZoneSameInstant(MALL_B_ZONE).format(SINCE_FORMAT))
                        .build())
                .retrieve()
                .body(MallBOrderResponse.class);

        if (response == null || response.orders() == null) {
            log.warn("モールBのレスポンスが空でした");
            return List.of();
        }
        return response.orders().stream()
                .map(this::toOrder)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Order> toOrder(MallBOrderResponse.MallBOrder source) {
        Optional<MallBStatusCode> status = MallBStatusCode.from(source.statusCode());
        if (status.isEmpty()) {
            log.warn("未知のステータスコードのため取込対象外にしました: no={} statusCode={}",
                    source.no(), source.statusCode());
            return Optional.empty();
        }
        if (!status.get().isImportable()) {
            log.debug("モール側で処理済みのため取込対象外です: no={} statusCode={}",
                    source.no(), source.statusCode());
            return Optional.empty();
        }

        Optional<OffsetDateTime> orderedAt = parseOrderedAt(source);
        if (orderedAt.isEmpty()) {
            return Optional.empty();
        }

        List<OrderItem> items = source.details().stream()
                .map(detail -> OrderItem.of(detail.itemCd(), detail.itemNm(), detail.price(), detail.cnt()))
                .toList();

        return Optional.of(Order.importedFrom(
                new MallOrderKey(mallId(), source.no()),
                source.buyer(),
                source.amount(),
                orderedAt.get(),
                items));
    }

    private Optional<OffsetDateTime> parseOrderedAt(MallBOrderResponse.MallBOrder source) {
        try {
            return Optional.of(LocalDateTime.parse(source.orderDatetime(), MALL_B_DATETIME)
                    .atZone(MALL_B_ZONE)
                    .toOffsetDateTime());
        } catch (DateTimeParseException | NullPointerException e) {
            log.warn("注文日時を解釈できないため取込対象外にしました: no={} orderDatetime={}",
                    source.no(), source.orderDatetime());
            return Optional.empty();
        }
    }
}
