package com.minioms.domain.stock;

import com.minioms.domain.DomainException;

/**
 * 引当しようとした商品が在庫マスタに登録されていないことを表す。
 *
 * <p>Why not: 在庫0として扱って引当失敗にしない。「在庫を切らしている」と
 * 「そもそも在庫管理されていない」は原因も対処も違う(前者は入荷待ち、
 * 後者はマスタ登録漏れ)。同じ失敗として潰すと運用が原因に辿り着けない。</p>
 */
public class StockNotFoundException extends DomainException {

    private final String productCode;

    public StockNotFoundException(String productCode) {
        super("在庫が登録されていない商品です: " + productCode);
        this.productCode = productCode;
    }

    public String getProductCode() {
        return productCode;
    }
}
