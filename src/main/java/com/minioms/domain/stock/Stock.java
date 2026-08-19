package com.minioms.domain.stock;

/**
 * 商品コード単位の在庫(集約ルート)。
 *
 * <p>在庫は「実在庫(倉庫にある数)」と「引当済(出荷が約束された数)」の2つの数で表す。
 * 引当は倉庫からモノを出す操作ではないため実在庫を減らさず、出荷して初めて減る。
 * この区別を持たないと、売れる数を読み違えて欠品か二重売りを起こす。</p>
 *
 * <p>Why not: 「引当可能数」を項目として持たない。実在庫と引当済から一意に決まる値を
 * 保存すると、3つの数の整合を保つ責任が生まれ、片方だけ更新された不整合が起きうる。</p>
 *
 * <p>Why not: 状態遷移と同じくイミュータブルにする。引当・出荷は同時実行の競合が
 * 起きやすく、「操作前の在庫」をそのまま保持できる方が競合の検出と再試行が単純になる。</p>
 *
 * @param id      永続化前は null(採番はDBに委ねる)
 * @param version 楽観ロック用。永続化前は 0
 */
public record Stock(Long id, String productCode, int quantityOnHand, int quantityAllocated, long version) {

    public Stock {
        if (productCode == null || productCode.isBlank()) {
            throw new IllegalArgumentException("商品コードは必須です");
        }
        if (quantityOnHand < 0) {
            throw new IllegalArgumentException("実在庫は0以上である必要があります: " + quantityOnHand);
        }
        if (quantityAllocated < 0) {
            throw new IllegalArgumentException("引当済数は0以上である必要があります: " + quantityAllocated);
        }
        if (quantityAllocated > quantityOnHand) {
            throw new IllegalArgumentException(
                    "引当済数が実在庫を超えています: 実在庫=%d 引当済=%d".formatted(quantityOnHand, quantityAllocated));
        }
    }

    /** 新規登録する在庫。まだ引当は無い */
    public static Stock of(String productCode, int quantityOnHand) {
        return new Stock(null, productCode, quantityOnHand, 0, 0L);
    }

    /** 他の受注に約束されていない、これから引き当てられる数 */
    public int availableQuantity() {
        return quantityOnHand - quantityAllocated;
    }

    /**
     * 出荷を約束する(引当)。実在庫は動かさない。
     *
     * @throws InsufficientStockException 引当可能数が足りない場合
     */
    public Stock allocate(int quantity) {
        requirePositive(quantity);
        if (quantity > availableQuantity()) {
            throw new InsufficientStockException(productCode, quantity, availableQuantity());
        }
        return new Stock(id, productCode, quantityOnHand, quantityAllocated + quantity, version);
    }

    /**
     * 引当を取り消す(キャンセル時)。モノは動いていないため実在庫は変えない。
     *
     * <p>Why not: 引当済を超える解除を {@link InsufficientStockException} にしない。
     * 在庫が足りないのではなく、引当の記録と受注の状態が食い違っている状態であり、
     * 業務上ありえない。呼び出し側の不具合として扱う。</p>
     */
    public Stock release(int quantity) {
        requirePositive(quantity);
        requireAllocated(quantity, "解除");
        return new Stock(id, productCode, quantityOnHand, quantityAllocated - quantity, version);
    }

    /** 引当済のモノを出荷する。実在庫と引当済の両方が減る */
    public Stock shipOut(int quantity) {
        requirePositive(quantity);
        requireAllocated(quantity, "出荷");
        return new Stock(id, productCode, quantityOnHand - quantity, quantityAllocated - quantity, version);
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量は1以上である必要があります: " + quantity);
        }
    }

    private void requireAllocated(int quantity, String operation) {
        if (quantity > quantityAllocated) {
            throw new IllegalStateException(
                    "引当済数を超える%sです: 商品=%s 引当済=%d 要求=%d"
                            .formatted(operation, productCode, quantityAllocated, quantity));
        }
    }
}
