package com.minioms.presentation.order;

/**
 * APIで指定されたモールコードが存在しないことを表す。
 *
 * <p>Why not: ドメインの例外({@code DomainException})にはしない。
 * 業務ルールの違反ではなく、API入力の検証結果であるため presentation 層に置く。</p>
 */
public class UnknownMallCodeException extends RuntimeException {

    public UnknownMallCodeException(String mallCode) {
        super("存在しないモールコードです: " + mallCode);
    }
}
