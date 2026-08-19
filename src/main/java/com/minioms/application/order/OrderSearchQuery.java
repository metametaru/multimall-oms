package com.minioms.application.order;

/**
 * 受注一覧の参照ポート。実装は infrastructure 層が担う。
 *
 * <p>Why not: {@link OrderRepository} に検索を足さない。更新系は集約を丸ごと扱い、
 * 参照系は表示に必要な列だけを扱うという読み書きの非対称を、ポートの分離として残す。
 * 同じポートに同居させると、集約を返す責務と読み取りモデルを返す責務が混ざる。</p>
 */
public interface OrderSearchQuery {

    OrderSearchResult search(OrderSearchCriteria criteria);
}
