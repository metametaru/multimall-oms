package com.minioms.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * ログイン試行の制限設定。
 *
 * @param maxFailures 連続して許容する失敗回数
 * @param lockout     上限に達したあと受け付けない時間
 */
@ConfigurationProperties(prefix = "minioms.security.login")
record LoginAttemptProperties(Integer maxFailures, Duration lockout) {

    LoginAttemptProperties {
        if (maxFailures == null) {
            maxFailures = 5;
        }
        if (lockout == null) {
            lockout = Duration.ofMinutes(1);
        }
    }
}
