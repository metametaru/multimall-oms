package com.minioms.domain.order;

import com.minioms.domain.DomainException;

/**
 * 冪等キー(モールID + モール側注文番号)が既に存在する受注を登録しようとしたことを表す。
 *
 * <p>取込ユースケースはこれを「既に取込済みのためスキップ」という正常系として扱う。
 * 例外にしているのは、正常系かどうかの判断を呼び出し側の文脈に委ねるため
 * (再取込は正常だが、手入力からの重複登録は異常として扱いたい)。</p>
 */
public class DuplicateMallOrderException extends DomainException {

    private final MallOrderKey mallOrderKey;

    public DuplicateMallOrderException(MallOrderKey mallOrderKey) {
        super("既に取込済みの受注です: %s".formatted(mallOrderKey));
        this.mallOrderKey = mallOrderKey;
    }

    public MallOrderKey getMallOrderKey() {
        return mallOrderKey;
    }
}
