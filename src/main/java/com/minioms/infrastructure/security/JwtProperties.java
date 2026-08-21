package com.minioms.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * アクセストークンの設定。
 *
 * @param secret HMAC署名鍵。未設定なら起動ごとにランダム生成する(ローカル開発向け)
 * @param ttl    発行から失効までの長さ
 */
@ConfigurationProperties(prefix = "minioms.security.jwt")
record JwtProperties(String secret, Duration ttl) {

    JwtProperties {
        if (ttl == null) {
            ttl = Duration.ofHours(1);
        }
    }
}
