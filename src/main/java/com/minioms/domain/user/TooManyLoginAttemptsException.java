package com.minioms.domain.user;

import java.time.Duration;

/**
 * ログインの試行が続けて失敗し、しばらく受け付けない状態であることを表す。
 *
 * <p>Why not: {@link InvalidCredentialsException} と同じ扱いにしない。資格情報の誤りは
 * 「入れ直せば通る」が、この状態は<b>正しい資格情報でも通らない</b>。同じ応答にすると、
 * 利用者は正しいパスワードを疑い続けることになる。</p>
 *
 * <p>Why not: {@code DomainException} を継承しない。認証にまつわる失敗であり、
 * 受注の現在状態と衝突した(409)わけではない。</p>
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException(Duration lockout) {
        super("ログインの試行回数が上限に達しました。%d秒後にもう一度お試しください"
                .formatted(lockout.toSeconds()));
    }
}
