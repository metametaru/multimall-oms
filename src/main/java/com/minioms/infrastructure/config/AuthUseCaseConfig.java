package com.minioms.infrastructure.config;

import com.minioms.application.auth.AccessTokenIssuer;
import com.minioms.application.auth.AuthenticateUserUseCase;
import com.minioms.application.auth.LoginAttemptPolicy;
import com.minioms.application.auth.PasswordHasher;
import com.minioms.application.auth.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 認証のユースケースを Bean として登録する。
 * 受注・在庫と同じく、application 層にDIの知識を持ち込まないための置き場。
 */
@Configuration
class AuthUseCaseConfig {

    @Bean
    AuthenticateUserUseCase authenticateUserUseCase(UserRepository userRepository,
                                                    PasswordHasher passwordHasher,
                                                    AccessTokenIssuer accessTokenIssuer,
                                                    LoginAttemptPolicy loginAttemptPolicy) {
        return new AuthenticateUserUseCase(userRepository, passwordHasher, accessTokenIssuer, loginAttemptPolicy);
    }
}
