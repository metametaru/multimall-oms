package com.minioms.presentation.auth;

import com.minioms.application.auth.AccessToken;

/**
 * ログイン成功時の応答。
 *
 * @param accessToken 以降のリクエストの Authorization ヘッダに載せる値
 * @param tokenType   ヘッダに前置する種別。{@code Authorization: Bearer <accessToken>} となる
 * @param expiresIn   失効までの秒数
 */
record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {

    private static final String BEARER = "Bearer";

    static AccessTokenResponse from(AccessToken token) {
        return new AccessTokenResponse(token.value(), BEARER, token.expiresInSeconds());
    }
}
