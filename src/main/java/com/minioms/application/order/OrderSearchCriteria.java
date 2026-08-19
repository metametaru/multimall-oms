package com.minioms.application.order;

import com.minioms.domain.order.OrderStatus;

/**
 * 受注検索の条件。null は「その条件で絞り込まない」を意味する。
 *
 * @param status 絞り込むステータス。null なら全ステータス
 * @param mallId 絞り込むモールID。null なら全モール
 * @param page   0起点のページ番号
 * @param size   1ページの件数
 */
public record OrderSearchCriteria(OrderStatus status, Long mallId, int page, int size) {

    /** Why not: 上限を設けずに要求どおりの件数を返さない。全件取得の要求1つでDBとメモリを圧迫するため */
    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    public OrderSearchCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("ページ番号は0以上である必要があります: " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("ページサイズは1以上である必要があります: " + size);
        }
        size = Math.min(size, MAX_SIZE);
    }
}
