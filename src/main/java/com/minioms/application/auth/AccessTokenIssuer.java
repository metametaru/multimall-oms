package com.minioms.application.auth;

import com.minioms.domain.user.User;

/**
 * 認証済みの利用者にアクセストークンを発行するポート。実装は infrastructure 層が担う。
 *
 * <p>Why not: application 層が JWT という形式を知らないようにする。
 * ユースケースにとって必要なのは「認証できた事実を、以降のリクエストで
 * 提示できる形に変える」ことだけで、それが JWT である必然性はない。</p>
 */
public interface AccessTokenIssuer {

    AccessToken issue(User user);
}
