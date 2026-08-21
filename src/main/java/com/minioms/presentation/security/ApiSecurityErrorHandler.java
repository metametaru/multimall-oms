package com.minioms.presentation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * セキュリティフィルタで弾かれたリクエストの応答。
 *
 * <p>Why not: Spring Security の既定の応答に任せない。既定は本文が空のため、
 * {@code ApiExceptionHandler} が返す ProblemDetail と形が揃わず、
 * クライアントがエラーの読み取り方を2通り持つことになる。</p>
 *
 * <p>Why not: 403 の本文に不足している権限名を書かない。どのロールなら通るかを
 * 教えることになり、攻撃者にとっては権限昇格の的が絞れる情報になる。</p>
 */
@Component
@RequiredArgsConstructor
class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 認証されていない: 401。トークンが無い・期限切れ・署名が不正のいずれも含む */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "認証が必要です",
                "有効なアクセストークンを Authorization ヘッダに指定してください");
    }

    /** 認証済みだが権限が足りない: 403 */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "権限がありません",
                "この操作を行う権限がありません");
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
