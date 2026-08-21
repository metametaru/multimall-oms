package com.minioms.presentation.order;

import com.minioms.application.mall.MallDirectory;
import com.minioms.application.order.FindOrdersUseCase;
import com.minioms.application.order.OrderLifecycleUseCase;
import com.minioms.application.order.OrderSearchCriteria;
import com.minioms.application.order.OrderSearchResult;
import com.minioms.domain.order.Order;
import com.minioms.domain.order.OrderStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 受注API。オペレーターの受注一覧・詳細と、受注を業務フローに沿って進める操作の入口。
 *
 * <p>Why not: ここに業務判断を書かない。Controller の責務は HTTP の語彙
 * (クエリパラメータ・ステータスコード・JSON)とアプリケーションの語彙の変換に限る。</p>
 *
 * <p>Why not: モールIDをAPIに露出しない。IDはDBが採番する内部識別子で環境ごとに
 * 変わりうるため、外部との契約はモールコード({@code MALL_A})で結ぶ。</p>
 *
 * <p>Why not: ステータス変更を {@code PATCH /orders/{id}} の一本にまとめない。
 * 「statusフィールドを書き換える」形にすると、クライアントが状態機械を知っている
 * 前提になり、出荷指示やキャンセルという業務操作の意味がAPIから消える。</p>
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
class OrderController {

    private final FindOrdersUseCase findOrdersUseCase;
    private final OrderLifecycleUseCase orderLifecycleUseCase;
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

    /**
     * 絞り込みに使えるステータスの一覧。
     *
     * <p>Why not: 画面側に選択肢を並べさせない。ステータスを追加したときに
     * 画面だけ古い一覧のまま残り、新しいステータスの受注が絞り込めなくなる。</p>
     */
    @GetMapping("/statuses")
    List<OrderStatusResponse> statuses() {
        return OrderStatusLabel.all();
    }

    @GetMapping("/{orderId}")
    OrderResponse detail(@PathVariable long orderId) {
        return toResponse(findOrdersUseCase.findById(orderId));
    }

    /** 内容確認の完了を記録する */
    @PostMapping("/{orderId}/confirmation")
    OrderResponse confirm(@PathVariable long orderId, @Valid @RequestBody StatusChangeRequest request) {
        return toResponse(orderLifecycleUseCase.confirm(orderId, request.version()));
    }

    /** 倉庫へ出荷を指示する。これ以降はキャンセルできない */
    @PostMapping("/{orderId}/shipping-instruction")
    OrderResponse instructShipping(@PathVariable long orderId, @Valid @RequestBody StatusChangeRequest request) {
        return toResponse(orderLifecycleUseCase.instructShipping(orderId, request.version()));
    }

    /** 出荷完了を記録する */
    @PostMapping("/{orderId}/shipment")
    OrderResponse ship(@PathVariable long orderId, @Valid @RequestBody StatusChangeRequest request) {
        return toResponse(orderLifecycleUseCase.ship(orderId, request.version()));
    }

    /** 受注をキャンセルする */
    @PostMapping("/{orderId}/cancellation")
    OrderResponse cancel(@PathVariable long orderId, @Valid @RequestBody StatusChangeRequest request) {
        return toResponse(orderLifecycleUseCase.cancel(orderId, request.version()));
    }

    /** 出荷済みの受注の返品を記録する */
    @PostMapping("/{orderId}/return")
    OrderResponse markReturned(@PathVariable long orderId, @Valid @RequestBody StatusChangeRequest request) {
        return toResponse(orderLifecycleUseCase.markReturned(orderId, request.version()));
    }

    private OrderResponse toResponse(Order order) {
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

    // 参照整合性で守られているため通常は解決できる。解決できない場合も表示自体は続行させる
    private String mallCodeOf(long mallId) {
        return mallDirectory.codeOf(mallId).orElse(null);
    }
}
