package com.minioms.infrastructure.security;

import com.minioms.application.auth.AccessToken;
import com.minioms.domain.user.Role;
import com.minioms.domain.user.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * アクセストークンの仕様。
 *
 * <p>トークンは「誰が」「いつまで」「どの権限で」を、改ざんできない形で運ぶ。
 * この3つが崩れると認可の判断そのものが信用できなくなるため、
 * 発行したトークンを実際に検証して確かめる。</p>
 */
@DisplayName("アクセストークン")
class JwtAccessTokenIssuerTest {

    private static final SecretKey KEY = keyOf("test-signing-key-for-mini-oms-32bytes");
    private static final SecretKey OTHER_KEY = keyOf("another-signing-key-for-mini-oms-32b");
    private static final Duration TTL = Duration.ofHours(1);

    // 検証側(NimbusJwtDecoder)は実時刻で期限を見るため、発行時刻も実時刻を基準にする。
    // 秒単位に丸めているのは、JWTの exp / iat が秒精度でしか表現されないため
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final User operator = new User(1L, "operator", "$2a$10$hash", Role.OPERATOR);

    @Test
    void 発行したトークンから利用者とロールを読み取れる() {
        AccessToken token = issuerAt(NOW).issue(operator);

        Jwt decoded = decoderFor(KEY).decode(token.value());

        assertThat(decoded.getSubject()).isEqualTo("operator");
        assertThat(decoded.getClaimAsStringList(JwtAccessTokenIssuer.ROLES_CLAIM))
                .containsExactly(Role.OPERATOR.name());
        // getIssuer() はURLとして解釈しようとするため、識別子は文字列のまま読む
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(JwtAccessTokenIssuer.ISSUER);
    }

    @Test
    void 失効までの秒数を設定した寿命と一致させる() {
        // クライアントは「あと何秒使えるか」だけで再ログインの判断ができる必要がある
        AccessToken token = issuerAt(NOW).issue(operator);

        assertThat(token.expiresInSeconds()).isEqualTo(TTL.toSeconds());
        assertThat(decoderFor(KEY).decode(token.value()).getExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void 有効期限を過ぎたトークンは受け付けない() {
        // 失効リストを持たない代わりに、期限が唯一の失効手段になる
        AccessToken expired = issuerAt(NOW.minus(TTL).minusSeconds(60)).issue(operator);

        assertThatThrownBy(() -> decoderFor(KEY).decode(expired.value()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 別の鍵で署名されたトークンは受け付けない() {
        // 署名を検証しなければ、ロールを書き換えたトークンで権限を詐称できる
        AccessToken forged = new JwtAccessTokenIssuer(
                new NimbusJwtEncoder(new ImmutableSecret<>(OTHER_KEY)), TTL, fixedClock(NOW)).issue(operator);

        assertThatThrownBy(() -> decoderFor(KEY).decode(forged.value()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 参照専用の利用者にはVIEWERのロールだけを載せる() {
        User viewer = new User(2L, "viewer", "$2a$10$hash", Role.VIEWER);

        Jwt decoded = decoderFor(KEY).decode(issuerAt(NOW).issue(viewer).value());

        assertThat(decoded.getClaimAsStringList(JwtAccessTokenIssuer.ROLES_CLAIM))
                .isEqualTo(List.of(Role.VIEWER.name()));
    }

    private static JwtAccessTokenIssuer issuerAt(Instant now) {
        return new JwtAccessTokenIssuer(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), TTL, fixedClock(now));
    }

    private static NimbusJwtDecoder decoderFor(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static SecretKey keyOf(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
