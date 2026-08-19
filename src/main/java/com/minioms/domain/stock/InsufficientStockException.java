package com.minioms.domain.stock;

import com.minioms.domain.DomainException;

/**
 * 引当可能数が足りず、受注に在庫を引き当てられないことを表す。
 *
 * <p>実在庫が残っていても、他の受注に約束済み(引当済)の分は引き当てられない。
 * 欠品として運用で対処する業務上の失敗であり、システムの異常ではない。</p>
 */
public class InsufficientStockException extends DomainException {

    private final String productCode;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InsufficientStockException(String productCode, int requestedQuantity, int availableQuantity) {
        super("在庫が不足しています: 商品=%s 要求=%d 引当可能=%d"
                .formatted(productCode, requestedQuantity, availableQuantity));
        this.productCode = productCode;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public String getProductCode() {
        return productCode;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
