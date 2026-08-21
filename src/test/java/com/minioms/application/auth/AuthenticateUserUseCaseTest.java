package com.minioms.application.auth;

import com.minioms.domain.user.InvalidCredentialsException;
import com.minioms.domain.user.TooManyLoginAttemptsException;
import com.minioms.domain.user.Role;
import com.minioms.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 認証の仕様。
 *
 * <p>認証は「誰であるか」を確定させるだけで、その人が何をできるかは判断しない。
 * ここで決まるのは、発行するトークンに何を載せるか(＝以降の認可の材料)まで。</p>
 */
@DisplayName("認証")
class AuthenticateUserUseCaseTest {

    private static final int MAX_FAILURES = 5;

    private FakeUserRepository userRepository;
    private FakePasswordHasher passwordHasher;
    private RecordingTokenIssuer tokenIssuer;
    private AuthenticateUserUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        passwordHasher = new FakePasswordHasher();
        tokenIssuer = new RecordingTokenIssuer();
        useCase = new AuthenticateUserUseCase(userRepository, passwordHasher, tokenIssuer,
                new LoginAttemptPolicy(MAX_FAILURES, Duration.ofMinutes(1), Clock.systemUTC()));

        userRepository.save(User.of("operator", passwordHasher.hash("correct-password"), Role.OPERATOR));
    }

    @Test
    void 正しい資格情報ならアクセストークンを発行する() {
        AccessToken token = useCase.authenticate("operator", "correct-password");

        assertThat(token.value()).isNotBlank();
        assertThat(token.expiresInSeconds()).isPositive();
    }

    @Test
    void トークンは認証した利用者に対して発行される() {
        // 発行されたトークンが誰のものかは、この後の認可の判断材料そのものになる
        useCase.authenticate("operator", "correct-password");

        assertThat(tokenIssuer.issuedFor)
                .singleElement()
                .satisfies(user -> {
                    assertThat(user.username()).isEqualTo("operator");
                    assertThat(user.role()).isEqualTo(Role.OPERATOR);
                });
    }

    @Test
    void パスワードが違えば認証できない() {
        assertThatThrownBy(() -> useCase.authenticate("operator", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(tokenIssuer.issuedFor).isEmpty();
    }

    @Test
    void 存在しないユーザー名はパスワード誤りと同じ失敗として扱う() {
        // Why: 応答を区別すると、エラーの差分からユーザー名の実在を総当たりで確認できてしまう
        assertThatThrownBy(() -> useCase.authenticate("unknown-user", "correct-password"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(new InvalidCredentialsException().getMessage());
    }

    @Test
    void 存在しないユーザー名でもハッシュ照合を省略しない() {
        // 実在しないユーザーだけ応答が速いと、時間差から実在するユーザー名を絞り込める
        assertThatThrownBy(() -> useCase.authenticate("unknown-user", "correct-password"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(passwordHasher.matchCallCount).isEqualTo(1);
    }

    @Test
    void ユーザー名やパスワードが空なら認証できない() {
        assertThatThrownBy(() -> useCase.authenticate("", "correct-password"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> useCase.authenticate("operator", "  "))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> useCase.authenticate(null, null))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 失敗が続くと正しいパスワードでも受け付けなくなる() {
        // ハッシュ化は総当たりを遅くするだけで止めはしない。試行そのものを打ち切る
        for (int i = 0; i < MAX_FAILURES; i++) {
            assertThatThrownBy(() -> useCase.authenticate("operator", "wrong-password"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> useCase.authenticate("operator", "correct-password"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void 途中で成功すれば失敗の記録は持ち越さない() {
        assertThatThrownBy(() -> useCase.authenticate("operator", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
        useCase.authenticate("operator", "correct-password");

        for (int i = 0; i < MAX_FAILURES - 1; i++) {
            assertThatThrownBy(() -> useCase.authenticate("operator", "wrong-password"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        // 直前の成功で記録が消えているため、まだ上限には達しない
        assertThat(useCase.authenticate("operator", "correct-password").value()).isNotBlank();
    }

    // --- テストダブル(手書きのFake) ---

    private static final class FakeUserRepository implements UserRepository {
        private final List<User> users = new ArrayList<>();

        @Override
        public Optional<User> findByUsername(String username) {
            return users.stream().filter(user -> user.username().equals(username)).findFirst();
        }

        @Override
        public User save(User user) {
            User stored = new User((long) (users.size() + 1), user.username(), user.passwordHash(), user.role());
            users.add(stored);
            return stored;
        }
    }

    /** 照合回数を数えられる、ハッシュ化を模しただけのFake */
    private static final class FakePasswordHasher implements PasswordHasher {
        private int matchCallCount;

        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            matchCallCount++;
            return hash(rawPassword).equals(passwordHash);
        }
    }

    private static final class RecordingTokenIssuer implements AccessTokenIssuer {
        private final List<User> issuedFor = new ArrayList<>();

        @Override
        public AccessToken issue(User user) {
            issuedFor.add(user);
            return new AccessToken("token-for-" + user.username(), 3600L, user.role());
        }
    }
}
