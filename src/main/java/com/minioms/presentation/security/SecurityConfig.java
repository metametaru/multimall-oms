package com.minioms.presentation.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 暫定のセキュリティ設定。全エンドポイントを開放している。
 *
 * <p><strong>第3週の認証実装で本実装に差し替える。</strong>
 * Spring Security を入れた時点で全エンドポイントが Basic 認証必須になり、
 * 業務ロジックの動作確認ができなくなるため、認証を後回しにする間だけ開放する。</p>
 *
 * <p>Why not: Spring Security の依存自体を後から追加する案は採らない。
 * 認証を後付けするとフィルタ順序やCSRF・セッション方針の見直しが広範囲に及ぶため、
 * 最初から依存を入れた上で「意図的に開放している」ことをコードに残す。</p>
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Why not: CSRF は無効のままにする。セッションを持たないREST APIであり、
                // 認証はトークン方式を予定しているため、CSRFトークンの配布経路が存在しない
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
