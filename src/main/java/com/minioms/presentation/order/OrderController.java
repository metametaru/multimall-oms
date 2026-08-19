package com.minioms.presentation.order;

import com.minioms.application.mall.MallDirectory;
import com.minioms.application.order.FindOrdersUseCase;
import com.minioms.application.order.OrderSearchCriteria;
import com.minioms.application.order.OrderSearchResult;
import com.minioms.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 受注参照API。オペレーターの受注一覧・受注詳細の入口。
 *
 * <p>Why not: ここに業務判断を書かない。Controller の責務は HTTP の語彙
 * (クエリパラメータ・ステータスコード・JSON)とアプリケーションの語彙の変換に限る。</p>
 *
 * <p>Why not: モールIDをAPIに露出しない。IDはDBが採番する内部識別子で環境ごとに
 * 変わりうるため、外部との契約はモールコード({@code MALL_A})で結ぶ。</p>
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
class OrderController {

    private final FindOrdersUseCase findOrdersUseCase;
    private final MallDirectory mallDirectory;

    @GetMapping
    OrderListResponse list(@RequestParam(required = false) OrderStatus status,
                           @RequestParam(required = false) String mallCode,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {

        OrderSearchCriteria criteria = new OrderSearchCriteria(status, resolveMallId(mallCode), page, size);
        OrderSearchResult result = findOrdersUseCase.search(criteria);

        return new OrderListResponse(
                result.orders().stream()
                        .map(summary -> OrderSummaryResponse.from(summary, mallCodeOf(summary.mallId())))
                        .toList(),
                result.totalCount(),
                criteria.page(),
                criteria.size());
    }

    @GetMapping("/{orderId}")
    OrderResponse detail(@PathVariable long orderId) {
        var order = findOrdersUseCase.findById(orderId);
        return OrderResponse.from(order, mallCodeOf(order.mallOrderKey().mallId()));
    }

    /**
     * Why not: 未知のモールコードを「該当0件」で返さない。
     * 絞り込みの打ち間違いが空の一覧として見え、原因に気づけないため。
     */
    private Long resolveMallId(String mallCode) {
        if (mallCode == null || mallCode.isBlank()) {
            return null;
        }
        return mallDirectory.idOf(mallCode)
                .orElseThrow(() -> new UnknownMallCodeException(mallCode));
    }

    // 参照整合性で守られているため通常は解決できる。解決できない場合も一覧表示自体は続行させる
    private String mallCodeOf(long mallId) {
        return mallDirectory.codeOf(mallId).orElse(null);
    }
}
