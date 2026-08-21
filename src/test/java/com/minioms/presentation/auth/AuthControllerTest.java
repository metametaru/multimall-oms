package com.minioms.presentation.auth;

import com.minioms.application.auth.AccessToken;
import com.minioms.application.auth.AuthenticateUserUseCase;
import com.minioms.domain.user.InvalidCredentialsException;
import com.minioms.domain.user.TooManyLoginAttemptsException;
import com.minioms.domain.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 認証APIのHTTP契約。
 *
 * <p>クライアントはこの応答だけで「次のリクエストに何を載せるか」「いつ取り直すか」を
 * 判断できる必要がある。認可のルールそのものは {@code ApiAuthorizationTest} が扱う。</p>
 */
@DisplayName("認証API")
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticateUserUseCase authenticateUserUseCase;

    @Test
    void 認証に成功するとトークンと有効期限を返す() throws Exception {
        given(authenticateUserUseCase.authenticate("operator", "correct-password"))
                .willReturn(new AccessToken("issued-token", 3600L, Role.OPERATOR));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"correct-password"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("issued-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    void 認証された利用者の権限を応答に含める() throws Exception {
        // Why: 画面がトークンを復号して権限を読む形にすると、画面が自分の権限を
        // 自称する経路になる。出どころをサーバーの応答に一本化する
        given(authenticateUserUseCase.authenticate("viewer", "viewer-password"))
                .willReturn(new AccessToken("issued-token", 3600L, Role.VIEWER));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"viewer","password":"viewer-password"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    void 認証に失敗すると401を返す() throws Exception {
        willThrow(new InvalidCredentialsException())
                .given(authenticateUserUseCase).authenticate(any(), any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("認証に失敗しました"));
    }

    @Test
    void 試行回数の上限に達したら429を返す() throws Exception {
        // Why: 401 にまとめない。401 は「入れ直せば通る」を意味するが、
        // この状態は正しい資格情報でも通らない
        willThrow(new TooManyLoginAttemptsException(Duration.ofMinutes(1)))
                .given(authenticateUserUseCase).authenticate(any(), any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"whatever"}"""))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("試行回数が多すぎます"));
    }

    @Test
    void 応答にパスワードを含めない() throws Exception {
        given(authenticateUserUseCase.authenticate(any(), any()))
                .willReturn(new AccessToken("issued-token", 3600L, Role.OPERATOR));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"operator","password":"correct-password"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test
    void 資格情報が欠けたリクエストも401として扱う() throws Exception {
        // Why: 空のユーザー名を400、誤りを401と分けると、クライアントが
        // 「入力の誤り」と「認証の失敗」を2通りで扱うことになる
        willThrow(new InvalidCredentialsException())
                .given(authenticateUserUseCase).authenticate(any(), any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}"""))
                .andExpect(status().isUnauthorized());
    }
}
