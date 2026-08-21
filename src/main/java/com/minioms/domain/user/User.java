package com.minioms.domain.user;

/**
 * システムの利用者。
 *
 * <p>Why not: 平文パスワードを保持できる形にしない。ハッシュしか受け取らないことで、
 * 平文のまま永続化する経路をコンパイル時に塞ぐ。ハッシュ化の方式(BCrypt)は
 * インフラの都合なので、ドメインは「ハッシュ済みの文字列」までしか知らない。</p>
 *
 * @param id 永続化前は null(採番はDBに委ねる)
 */
public record User(Long id, String username, String passwordHash, Role role) {

    public User {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("ユーザー名は必須です");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("パスワードハッシュは必須です");
        }
        if (role == null) {
            throw new IllegalArgumentException("ロールは必須です");
        }
    }

    /** 新規登録する利用者 */
    public static User of(String username, String passwordHash, Role role) {
        return new User(null, username, passwordHash, role);
    }
}
