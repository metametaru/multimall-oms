package com.minioms.application.auth;

import com.minioms.domain.user.User;

import java.util.Optional;

/**
 * 利用者の永続化ポート。実装は infrastructure 層が担う。
 */
public interface UserRepository {

    Optional<User> findByUsername(String username);

    /** 利用者を登録する。IDを持つ利用者の更新はスコープ外(利用者管理APIは作らない) */
    User save(User user);
}
