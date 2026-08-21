package com.minioms.infrastructure.persistence.user;

import com.minioms.TestcontainersConfiguration;
import com.minioms.application.auth.UserRepository;
import com.minioms.domain.user.Role;
import com.minioms.domain.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 利用者リポジトリの仕様。
 *
 * <p>認可の判断はDBに入っているロールを信頼して行う。そのため
 * 「同名の利用者が二重にできない」「未知のロールが入らない」の2点は
 * アプリを迂回した直接SQLでも成立していなければならない。</p>
 */
@DisplayName("利用者リポジトリ")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class UserRepositoryAdapterTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void 保存した利用者はユーザー名で取り出せて内容が往復する() {
        userRepository.save(User.of("test-operator", "$2a$10$abcdefghijklmnopqrstuv", Role.OPERATOR));

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findByUsername("test-operator"))
                .get()
                .satisfies(user -> {
                    assertThat(user.id()).isNotNull();
                    assertThat(user.passwordHash()).isEqualTo("$2a$10$abcdefghijklmnopqrstuv");
                    assertThat(user.role()).isEqualTo(Role.OPERATOR);
                });
    }

    @Test
    void 存在しないユーザー名は空で返る() {
        // 認証側で「見つからない」と「エラー」を取り違えないよう、例外にしない
        assertThat(userRepository.findByUsername("no-such-user")).isEmpty();
    }

    @Test
    void 同じユーザー名の利用者は二重に登録できない() {
        // 重複を許すと、どちらの権限で認証されるかが登録順に依存して不定になる
        userRepository.save(User.of("duplicated", "$2a$10$abcdefghijklmnopqrstuv", Role.VIEWER));
        entityManager.flush();

        assertThatThrownBy(() ->
                userRepository.save(User.of("duplicated", "$2a$10$zyxwvutsrqponmlkjihgfe", Role.OPERATOR)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 未知のロールはDBが拒否する() {
        // アプリを迂回した手作業のSQLで綴りを誤ると、認可の判定が静かに壊れる
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)",
                "broken-role", "$2a$10$abcdefghijklmnopqrstuv", "ADMIN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ロールはenum名の文字列としてDBに入る() {
        // 序数で保存されていると、enum の並び順を変えただけで既存利用者の権限が化ける
        userRepository.save(User.of("stored-as-name", "$2a$10$abcdefghijklmnopqrstuv", Role.VIEWER));
        entityManager.flush();

        String role = jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE username = ?", String.class, "stored-as-name");

        assertThat(role).isEqualTo("VIEWER");
    }
}
