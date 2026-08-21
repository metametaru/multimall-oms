package com.minioms.infrastructure.persistence.user;

import com.minioms.domain.user.User;

/** ドメインモデルとJPAエンティティの相互変換 */
final class UserMapper {

    private UserMapper() {
    }

    static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(user.id(), user.username(), user.passwordHash(), user.role());
    }

    static User toDomain(UserJpaEntity entity) {
        return new User(entity.getId(), entity.getUsername(), entity.getPasswordHash(), entity.getRole());
    }
}
