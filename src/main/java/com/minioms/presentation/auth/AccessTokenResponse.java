package com.minioms.presentation.auth;

import com.minioms.application.auth.AccessToken;
import com.minioms.domain.user.Role;

/**
 * ログイン成功時の応答。
 *
 * @param accessToken 以降のリクエストの Authorization ヘッダに載せる値
 * @param tokenType   ヘッダに前置する種別。{@code Authorization: Bearer <accessToken>} となる
 * @param expiresIn   失効までの秒数
 * @param role        認証された利用者の権限
 */
record AccessTokenResponse(String accessToken, String tokenType, long expiresIn, Role role) {

    private static final String BEARER = "Bearer";

    /**
     * Why not: ロールをトークンから画面側で読み取らせない。JWTのペイロードは
     * 誰でも復号でき、画面が「自分は OPERATOR だ」と自称する経路を作ることになる。
     * 応答として明示的に返す形にして、出どころをサーバーに一本化する。
     *
     * <p>この値は表示の出し分けにだけ使う。ボタンを隠すことは防御ではなく、
     * 操作を許すかどうかは毎回サーバーが判断する。</p>
     */
    static AccessTokenResponse from(AccessToken token) {
        return new AccessTokenResponse(token.value(), BEARER, token.expiresInSeconds(), token.role());
    }
}
