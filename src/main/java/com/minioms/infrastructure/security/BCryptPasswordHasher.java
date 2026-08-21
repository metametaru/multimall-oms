package com.minioms.infrastructure.security;

import com.minioms.application.auth.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring Security の {@link PasswordEncoder} を application 層のポートに適合させる。
 *
 * <p>Why not: 自前でハッシュ化を実装しない。ソルトの生成やストレッチング回数の管理は
 * 誤ると静かに強度が落ちる箇所で、ライブラリの既定に従う方が安全。</p>
 */
class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
