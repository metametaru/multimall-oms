package com.minioms.presentation.stock;

import com.minioms.domain.stock.Stock;

/**
 * 在庫のレスポンス。
 *
 * @param quantityOnHand    実在庫(倉庫にある数)
 * @param quantityAllocated 引当済(出荷が約束された数)
 * @param availableQuantity これから引き当てられる数
 */
record StockResponse(
        String productCode,
        int quantityOnHand,
        int quantityAllocated,
        int availableQuantity) {

    static StockResponse from(Stock stock) {
        // 引当可能数はドメインの計算結果をそのまま返す。表示側で引き算すると計算が二重になる
        return new StockResponse(
                stock.productCode(), stock.quantityOnHand(), stock.quantityAllocated(), stock.availableQuantity());
    }
}
