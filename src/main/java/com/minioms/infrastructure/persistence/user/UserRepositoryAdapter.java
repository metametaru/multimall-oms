package com.minioms.infrastructure.persistence.user;

import com.minioms.application.auth.UserRepository;
import com.minioms.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * {@link UserRepository} のJPA実装。
 */
@Repository
@RequiredArgsConstructor
class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(UserMapper::toDomain);
    }

    @Override
    @Transactional
    public User save(User user) {
        // Why not: ユニーク制約違反を専用の業務例外に変換しない。利用者の登録経路は
        // 起動時のデモ投入だけで、重複は「起動処理の不具合」であって業務の分岐ではない。
        // DataIntegrityViolationException のまま起動を失敗させる方が原因が早く分かる。
        return UserMapper.toDomain(jpaRepository.saveAndFlush(UserMapper.toEntity(user)));
    }
}
