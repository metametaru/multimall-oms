package com.minioms.application.auth;

import com.minioms.domain.user.InvalidCredentialsException;
import com.minioms.domain.user.TooManyLoginAttemptsException;
import com.minioms.domain.user.User;

/**
 * ユーザー名とパスワードを検証し、アクセストークンを発行する。
 *
 * <p>以降のリクエストはこのトークンだけで認証される。ここが資格情報を扱う唯一の入口。</p>
 */
public class AuthenticateUserUseCase {

    /**
     * 存在しないユーザーでも照合処理を通すためのダミーハッシュ。
     * これ自体は誰のパスワードでもなく、比較を空振りさせるためだけに使う。
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokenIssuer;
    private final LoginAttemptPolicy loginAttemptPolicy;

    public AuthenticateUserUseCase(UserRepository userRepository,
                                   PasswordHasher passwordHasher,
                                   AccessTokenIssuer accessTokenIssuer,
                                   LoginAttemptPolicy loginAttemptPolicy) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
        this.loginAttemptPolicy = loginAttemptPolicy;
    }

    /**
     * 資格情報を検証してトークンを発行する。
     *
     * @throws InvalidCredentialsException   ユーザー名またはパスワードが一致しない場合
     * @throws TooManyLoginAttemptsException 失敗が続き、しばらく受け付けない状態の場合
     */
    public AccessToken authenticate(String username, String rawPassword) {
        if (isBlank(username) || isBlank(rawPassword)) {
            throw new InvalidCredentialsException();
        }

        // ハッシュ化は意図的に遅いだけで、試行そのものを止めはしない。
        // 時間をかければ通る状態を残さないよう、照合の前に回数で打ち切る
        loginAttemptPolicy.verifyAccepting(username);

        User user = userRepository.findByUsername(username).orElse(null);

        // Why not: 見つからない時点で即座に失敗させない。ハッシュ照合は意図的に重い処理のため、
        // 実在しないユーザーだけ応答が速いと、時間差から実在するユーザー名を絞り込めてしまう。
        // 空振りと分かっていてもダミーハッシュとの照合を通し、処理時間を揃える。
        if (user == null) {
            passwordHasher.matches(rawPassword, DUMMY_HASH);
            loginAttemptPolicy.recordFailure(username);
            throw new InvalidCredentialsException();
        }

        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
            loginAttemptPolicy.recordFailure(username);
            throw new InvalidCredentialsException();
        }

        loginAttemptPolicy.recordSuccess(username);
        return accessTokenIssuer.issue(user);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
