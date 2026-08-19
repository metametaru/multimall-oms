package com.minioms.application.stock;

import com.minioms.domain.stock.DuplicateStockException;
import com.minioms.domain.stock.Stock;

import java.util.List;
import java.util.Optional;

/**
 * 在庫の永続化ポート。実装は infrastructure 層が担う。
 */
public interface StockRepository {

    /**
     * 在庫を保存する。IDを持たない在庫は新規登録、持つ在庫は更新となる。
     *
     * @throws DuplicateStockException 新規登録で商品コードが既に存在した場合
     */
    Stock save(Stock stock);

    Optional<Stock> findByProductCode(String productCode);

    /**
     * 引当・出荷のために在庫を取得する。取得した在庫は、同じ処理が終わるまで
     * 他の処理から更新できない(行ロック)。
     *
     * <p>Why not: 楽観ロックにしない。在庫は同一商品への同時更新が構造的に多く、
     * 楽観ロックだと競合のたびに操作が失敗し、呼び出し側に再試行の責任が生まれる。
     * 在庫の引当は待たせてでも直列に通す方が、運用も実装も単純になる。</p>
     */
    Optional<Stock> findByProductCodeForUpdate(String productCode);

    List<Stock> findAll();
}
