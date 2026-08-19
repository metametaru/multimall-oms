package com.minioms.domain.order;

/**
 * 受注の冪等キー。モールIDとモール側注文番号の組で受注を一意に識別する。
 *
 * <p>Why not: 注文番号だけを一意キーにする案は採らない。モールごとに採番体系が
 * 独立しており、異なるモールで同じ注文番号が発行されうるため。</p>
 */
public record MallOrderKey(long mallId, String mallOrderNumber) {

    public MallOrderKey {
        if (mallId <= 0) {
            throw new IllegalArgumentException("モールIDが不正です: " + mallId);
        }
        if (mallOrderNumber == null || mallOrderNumber.isBlank()) {
            throw new IllegalArgumentException("モール側注文番号は必須です");
        }
    }
}
