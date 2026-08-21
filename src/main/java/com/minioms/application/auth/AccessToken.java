package com.minioms.application.auth;

import com.minioms.domain.user.Role;

/**
 * 発行済みのアクセストークン。
 *
 * <p>Why not: 有効期限を「失効する時刻」で返さない。クライアントとサーバーの時計が
 * ずれていると、絶対時刻はクライアント側で正しく解釈できない。
 * 「あと何秒使えるか」なら受け取った側の時計だけで判断できる。</p>
 *
 * @param value            署名済みトークン文字列
 * @param expiresInSeconds 発行時点から失効までの秒数
 * @param role             認証された利用者の権限。画面の出し分けにだけ使う
 */
public record AccessToken(String value, long expiresInSeconds, Role role) {
}
