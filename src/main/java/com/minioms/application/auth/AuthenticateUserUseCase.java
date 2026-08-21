package com.minioms.application.auth;

import com.minioms.domain.user.InvalidCredentialsException;
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

    public AuthenticateUserUseCase(UserRepository userRepository,
                                   PasswordHasher passwordHasher,
                                   AccessTokenIssuer accessTokenIssuer) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.accessTokenIssuer = accessTokenIssuer;
    }

    /**
     * 資格情報を検証してトークンを発行する。
     *
     * @throws InvalidCredentialsException ユーザー名またはパスワードが一致しない場合
     */
    public AccessToken authenticate(String username, String rawPassword) {
        if (isBlank(username) || isBlank(rawPassword)) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByUsername(username).orElse(null);

        // Why not: 見つからない時点で即座に失敗させない。ハッシュ照合は意図的に重い処理のため、
        // 実在しないユーザーだけ応答が速いと、時間差から実在するユーザー名を絞り込めてしまう。
        // 空振りと分かっていてもダミーハッシュとの照合を通し、処理時間を揃える。
        if (user == null) {
            passwordHasher.matches(rawPassword, DUMMY_HASH);
            throw new InvalidCredentialsException();
        }

        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        return accessTokenIssuer.issue(user);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
