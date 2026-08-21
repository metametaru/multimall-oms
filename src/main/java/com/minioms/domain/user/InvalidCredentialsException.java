package com.minioms.domain.user;

/**
 * ユーザー名またはパスワードが一致しないことを表す。
 *
 * <p>Why not: {@code DomainException} を継承しない。継承すると presentation 層の
 * 対応表で 409 に変換されるが、認証の失敗は「受注の現在状態と衝突した」のではなく
 * 「そもそも誰であるかを確認できなかった」状態であり、401 が正しい。
 * 「業務ルール違反は DomainException」という規約は、認証には当てはまらない。</p>
 *
 * <p>Why not: 「ユーザーが存在しない」と「パスワードが違う」を区別しない。
 * メッセージが分かれていると、応答の差からユーザー名の実在を総当たりで確認できてしまう。</p>
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("ユーザー名またはパスワードが正しくありません");
    }
}
