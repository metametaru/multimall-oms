package com.minioms.application.stock;

import com.minioms.domain.stock.Stock;

import java.util.List;

/**
 * 在庫を参照する。オペレーターが引当状況(何がいくつ押さえられているか)を確認する入口。
 */
public class FindStocksUseCase {

    private final StockRepository stockRepository;

    public FindStocksUseCase(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * 全在庫を商品コード順で返す。
     *
     * <p>Why not: ページングを付けない。在庫は取り扱い商品数までしか増えず、
     * 受注のように際限なく増える性質のものではない。</p>
     *
     * <p>Why not: 商品コード指定の単体参照は用意しない。在庫マスタ未登録という
     * 同じ状態が、参照では「見つからない(404)」、引当では「受注を進められない(409)」と
     * 別の意味を持つ。1つの例外に2つのHTTPステータスを持たせるより、
     * 一覧から読む形に絞る方が誤解が少ない。</p>
     */
    public List<Stock> findAll() {
        return stockRepository.findAll();
    }
}
