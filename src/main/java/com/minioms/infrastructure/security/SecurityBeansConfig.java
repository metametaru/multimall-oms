package com.minioms.infrastructure.security;

import com.minioms.application.auth.AccessTokenIssuer;
import com.minioms.application.auth.PasswordHasher;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;

/**
 * 認証・認可に使う部品の組み立て。
 *
 * <p>Why not: 署名鍵をソースコードやプロパティの既定値に埋め込まない。
 * 公開リポジトリに鍵が残ると、そのビルドで発行されたトークンを誰でも偽造できる。
 * 未設定時は起動ごとにランダム生成し、本番相当の構成では環境変数での注入を必須とする。</p>
 *
 * <p>Why not: 公開鍵暗号(RS256)にしない。トークンを発行するのも検証するのも
 * このアプリ1つで、鍵を第三者に配る必要がない。共有相手がいない場面で鍵ペアを持つと、
 * 運用する対象が増えるだけで守れるものが増えない。</p>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
class SecurityBeansConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityBeansConfig.class);

    /** HS256 の署名鍵に必要な長さ。これを下回る鍵は総当たりの余地を残す */
    private static final int MINIMUM_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.isBlank()) {
            log.warn("minioms.security.jwt.secret が未設定のため、署名鍵を起動ごとに生成します。"
                    + "再起動すると発行済みのトークンは無効になります。本番相当の構成では必ず設定してください。");
            return generateKey();
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            // Why not: 短い鍵を黙って引き伸ばさない。設定した本人が強度を誤解したまま運用に入る
            throw new IllegalStateException(
                    "minioms.security.jwt.secret は %d バイト以上必要です: 現在 %d バイト"
                            .formatted(MINIMUM_SECRET_BYTES, keyBytes.length));
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * トークンの {@code roles} クレームを Spring Security の権限に写す。
     *
     * <p>Why not: 既定のまま使わない。既定はクレーム {@code scope} を読み {@code SCOPE_} を
     * 前置するため、{@code hasRole("OPERATOR")} での判定と噛み合わない。</p>
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(JwtAccessTokenIssuer.ROLES_CLAIM);
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    AccessTokenIssuer accessTokenIssuer(JwtEncoder jwtEncoder, JwtProperties properties) {
        return new JwtAccessTokenIssuer(jwtEncoder, properties.ttl(), Clock.systemUTC());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    PasswordHasher passwordHasher(PasswordEncoder passwordEncoder) {
        return new BCryptPasswordHasher(passwordEncoder);
    }

    private static SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
            keyGenerator.init(MINIMUM_SECRET_BYTES * 8);
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 が利用できません", e);
        }
    }
}
