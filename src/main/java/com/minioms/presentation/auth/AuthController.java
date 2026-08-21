package com.minioms.presentation.auth;

import com.minioms.application.auth.AuthenticateUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証API。資格情報を受け取り、以降のリクエストで使うアクセストークンを返す。
 *
 * <p>Why not: ログアウトAPIを用意しない。トークンはサーバー側に状態を持たないため、
 * 「失効させた」と応答しても実際には有効期限まで使える。できないことをAPIとして
 * 見せる方が危険なので、クライアントがトークンを破棄する運用に寄せている。</p>
 *
 * <p>Why not: 利用者の登録・変更APIは作らない。利用者管理はこのシステムの主題ではなく、
 * 作れば権限昇格という別の攻撃面を抱え込む。ローカルデモ用の利用者は起動時に投入する。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;

    @PostMapping("/login")
    AccessTokenResponse login(@RequestBody LoginRequest request) {
        return AccessTokenResponse.from(
                authenticateUserUseCase.authenticate(request.username(), request.password()));
    }
}
