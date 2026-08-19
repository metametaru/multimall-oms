package com.minioms.application.order;

import com.minioms.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 受注一覧のための読み取りモデル。
 *
 * <p>Why not: 一覧に集約({@code Order})を使わない。集約は明細を必ず伴うため、
 * 一覧の件数分だけ明細のロードが走る。一覧に必要なのは受注単位の情報だけなので、
 * 表示に必要な列だけを持つ別の型を用意する。</p>
 *
 * <p>Why not: ここで業務判断(遷移可否など)は持たせない。読み取り専用の入れ物であり、
 * 業務ルールの置き場所は常にドメイン層とする。</p>
 */
public record OrderSummary(
        long id,
        long mallId,
        String mallOrderNumber,
        OrderStatus status,
        String customerName,
        BigDecimal totalAmount,
        OffsetDateTime orderedAt,
        long version) {
}
