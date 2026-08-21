package com.minioms.presentation.order;

import com.minioms.domain.order.OrderStatus;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 受注ステータスの表示名。
 *
 * <p>ステータスの値そのものは enum 名({@code NEW} など)のまま扱う。画面の絞り込みや
 * スタイルの出し分けはこの値を使い、人に見せる文字列だけをここで与える。
 * 表示名を値として保存・送信してしまうと、言い回しを変えただけで
 * 保存済みデータやクライアントの分岐が壊れる。</p>
 *
 * <p>Why not: 表示名を {@link OrderStatus} に持たせない。どう呼ぶかは画面の都合で
 * 変わりうる一方、ステータスそのものは業務の構造であり、変わる理由が違う。
 * ドメインに日本語の文言が入ると、表現を直すたびにドメインを触ることになる。</p>
 *
 * <p>Why not: 画面側に対応表を置かない。サーバーとブラウザの2箇所に同じ表が生まれ、
 * ステータスを追加したときに画面だけ enum 名を素で表示する状態になる。</p>
 */
final class OrderStatusLabel {

    private static final Map<OrderStatus, String> LABELS = new EnumMap<>(Map.of(
            OrderStatus.NEW, "新規受付",
            OrderStatus.CONFIRMED, "確認済",
            OrderStatus.SHIPPING_INSTRUCTED, "出荷指示済",
            OrderStatus.SHIPPED, "出荷完了",
            OrderStatus.CANCELLED, "キャンセル",
            OrderStatus.RETURNED, "返品"
    ));

    private OrderStatusLabel() {
    }

    /**
     * 表示名を返す。
     * Why not: 未定義のステータスを空文字にしない。画面が無言で空欄になるより、
     * enum 名がそのまま出て「表示名の追加漏れ」だと分かる方が早く直せる。
     */
    static String of(OrderStatus status) {
        return LABELS.getOrDefault(status, status.name());
    }

    /** 絞り込みの選択肢。画面がステータスの一覧を自前で持たないために返す */
    static List<OrderStatusResponse> all() {
        return Arrays.stream(OrderStatus.values())
                .map(status -> new OrderStatusResponse(status, of(status)))
                .toList();
    }
}
