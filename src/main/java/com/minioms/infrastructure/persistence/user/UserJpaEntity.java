package com.minioms.infrastructure.persistence.user;

import com.minioms.domain.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * users テーブルのJPAマッピング。ドメインの {@code User} とは別物であり、
 * 相互変換は {@link UserMapper} が担う。
 */
@Entity
@Table(name = "users")
class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    // Why not: ordinal で保存しない。enum の並び順を変えただけで既存行の権限が
    // 別のロールに化け、認可の穴になる
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected UserJpaEntity() {
        // JPA用
    }

    UserJpaEntity(Long id, String username, String passwordHash, Role role) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    // Why not: @PreUpdate を持たせない。利用者の更新経路は用意しておらず、
    // 変更されうる項目が無い
    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    Long getId() {
        return id;
    }

    String getUsername() {
        return username;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    Role getRole() {
        return role;
    }
}
