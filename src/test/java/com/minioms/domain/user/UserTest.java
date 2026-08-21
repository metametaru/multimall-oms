package com.minioms.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 利用者の仕様。
 *
 * <p>利用者が持つ業務ルールは「資格情報が欠けた状態では存在できない」の一点に尽きる。
 * 誰が何をできるかという認可の判断はここには無く、presentation 層が持つ。</p>
 */
@DisplayName("利用者")
class UserTest {

    @Test
    void ユーザー名が無ければ作れない() {
        assertThatThrownBy(() -> User.of("  ", "$2a$10$hash", Role.OPERATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ユーザー名");
    }

    @Test
    void パスワードハッシュが無ければ作れない() {
        // 認証できない利用者は存在しないのと同じであり、中途半端な行をDBに残さない
        assertThatThrownBy(() -> User.of("operator", "", Role.OPERATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("パスワードハッシュ");
    }

    @Test
    void ロールが無ければ作れない() {
        // ロール未設定を「権限なし」と黙って解釈すると、設定漏れが認可の穴として現れる
        assertThatThrownBy(() -> User.of("operator", "$2a$10$hash", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ロール");
    }

    @Test
    void 新規登録する利用者はIDを持たない() {
        User user = User.of("operator", "$2a$10$hash", Role.OPERATOR);

        assertThat(user.id()).isNull();
        assertThat(user.username()).isEqualTo("operator");
        assertThat(user.role()).isEqualTo(Role.OPERATOR);
    }
}
