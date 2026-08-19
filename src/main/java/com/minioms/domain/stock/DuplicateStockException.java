package com.minioms.domain.stock;

import com.minioms.domain.DomainException;

/**
 * 既に在庫が登録されている商品を、二重に登録しようとしたことを表す。
 *
 * <p>同じ商品の在庫行が2つあると、どちらを引き当てたかで引当可能数が食い違い、
 * 欠品と二重売りのどちらも起こしうる。商品コードの一意性は在庫管理の前提。</p>
 */
public class DuplicateStockException extends DomainException {

    private final String productCode;

    public DuplicateStockException(String productCode) {
        super("在庫が既に登録されている商品です: " + productCode);
        this.productCode = productCode;
    }

    public String getProductCode() {
        return productCode;
    }
}
