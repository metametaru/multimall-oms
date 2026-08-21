package com.minioms.infrastructure.security;

import com.minioms.application.auth.PasswordHasher;
import com.minioms.application.auth.UserRepository;
import com.minioms.domain.user.Role;
import com.minioms.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * ローカルデモ用の利用者を起動時に投入する。モールAPIのモックと同じく
 * {@code mock} プロファイルでのみ有効で、本番相当の構成では動かない。
 *
 * <p>Why not: Flyway のマイグレーションで INSERT しない。それをするとパスワードの
 * ハッシュがリポジトリに永久に残り、公開リポジトリでは全員が知る資格情報になる。
 * 起動時に生成すれば、リポジトリに残るのは「デモ用の平文パスワード」という
 * 意図が明らかな定数だけで済む。</p>
 *
 * <p>Why not: 既存ユーザーを上書きしない。起動のたびにパスワードが戻ると、
 * 動作確認中に変更した状態が消えて原因の分からない挙動になる。</p>
 */
@Component
@Profile("mock")
@RequiredArgsConstructor
class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    // ローカルデモ専用。mock プロファイルの外では投入されない
    private static final String OPERATOR_USERNAME = "operator";
    private static final String OPERATOR_PASSWORD = "operator-pass";
    private static final String VIEWER_USERNAME = "viewer";
    private static final String VIEWER_PASSWORD = "viewer-pass";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    @Override
    public void run(ApplicationArguments args) {
        seed(OPERATOR_USERNAME, OPERATOR_PASSWORD, Role.OPERATOR);
        seed(VIEWER_USERNAME, VIEWER_PASSWORD, Role.VIEWER);
    }

    private void seed(String username, String rawPassword, Role role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        userRepository.save(User.of(username, passwordHasher.hash(rawPassword), role));
        log.info("デモ用の利用者を作成しました: {} ({})", username, role);
    }
}
