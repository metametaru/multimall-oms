package com.minioms.presentation.auth;

/**
 * ログイン要求。
 *
 * <p>Why not: 項目に {@code @NotBlank} を付けない。空のユーザー名は「リクエストの形式の誤り」
 * ではなく資格情報の不備であり、400 で返すと認証失敗(401)と2通りの扱いが混ざる。
 * 資格情報に関する失敗は、内容にかかわらず 401 に揃える。</p>
 */
record LoginRequest(String username, String password) {
}
