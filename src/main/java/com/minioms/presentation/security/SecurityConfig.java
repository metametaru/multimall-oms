package com.minioms.presentation.security;

import com.minioms.domain.user.Role;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 認証・認可の設定。
 *
 * <p>認可の線は「見るだけの人({@link Role#VIEWER})」と「動かす人({@link Role#OPERATOR})」の
 * 1本だけ。参照は両方に許し、受注や在庫を動かす操作は OPERATOR に限る。</p>
 *
 * <p>Why not: 認可のルールをドメイン層に置かない。「出荷指示済みの受注はキャンセルできない」は
 * 受注そのものの性質なのでドメイン({@code OrderStatus})が持つが、「VIEWER はキャンセル APIを
 * 叩けない」は誰にその操作を任せるかという運用の取り決めであり、受注の性質ではない。
 * 前者は誰が操作しても変わらず、後者は組織の運用で変わる。変わる理由が違うものを同居させない。</p>
 *
 * <p>Why not: セッションを持たない。トークンだけで認証が完結する構成のため、
 * CSRFトークンの配布経路が存在せず、CSRF対策も不要になる。</p>
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    /** 認証を受けるための入口。ここだけは認証なしで通す必要がある */
    static final String LOGIN_PATH = "/api/auth/login";

    /** オペレーター画面を構成するファイル。1つずつ列挙し、増えたら明示的に足す */
    private static final String[] STATIC_RESOURCES = {"/", "/index.html", "/app.js", "/app.css", "/favicon.ico"};

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthenticationConverter jwtAuthenticationConverter,
                                    ApiSecurityErrorHandler apiSecurityErrorHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Why not: ERROR ディスパッチまで認可の対象にしない。既定では対象に含まれ、
                        // 本来 404 や 500 を返すべき場面が 403 に化けて原因が追えなくなる
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()

                        .requestMatchers(HttpMethod.POST, LOGIN_PATH).permitAll()

                        // 画面のファイルそのものは誰でも取得できてよい。中身は空の器で、
                        // 表示するデータはすべて認証付きのAPIから取りに行く。
                        // Why not: /** のようなワイルドカードで開けない。将来 static 配下に
                        // 置いたものが、意図せず無認証で読める状態になる
                        .requestMatchers(HttpMethod.GET, STATIC_RESOURCES).permitAll()

                        // モールAPIのモックは mock プロファイルでしか存在せず、
                        // 取込スケジューラが同一プロセスへHTTPで取りに行くため認証を挟まない
                        .requestMatchers("/mock/**").permitAll()

                        // 参照は両方のロールに許す
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAnyRole(Role.VIEWER.name(), Role.OPERATOR.name())

                        // 受注と在庫を動かす操作は OPERATOR だけ
                        .requestMatchers("/api/**").hasRole(Role.OPERATOR.name())

                        // Why not: 未定義のパスを permitAll にしない。エンドポイントを
                        // 追加したときに認可の指定を忘れると、無防備なまま公開される
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(apiSecurityErrorHandler)
                        .accessDeniedHandler(apiSecurityErrorHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(apiSecurityErrorHandler)
                        .accessDeniedHandler(apiSecurityErrorHandler))
                .build();
    }
}
