package com.minioms.infrastructure.security;

import com.minioms.application.auth.AccessToken;
import com.minioms.application.auth.AccessTokenIssuer;
import com.minioms.domain.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * JWT によるアクセストークンの発行。
 *
 * <p>Why not: トークンを自前で組み立てない。ヘッダとペイロードを手で連結する実装は
 * {@code alg=none} の受理や期限の検証漏れといった典型的な穴を踏みやすい。
 * 発行も検証も Spring Security(Nimbus)に任せ、ここは載せる内容だけを決める。</p>
 *
 * <p>Why not: 失効リストを持たない。持てば「ステートレスなトークン」という前提が崩れ、
 * 検証のたびにDB参照が必要になる。代わりに有効期限を短く保ち、
 * 「ログアウトしても最大 ttl の間はトークンが有効」という割り切りを設定として明示する。</p>
 */
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    /**
     * 発行元の識別子。検証側が「このアプリが出したトークンか」を判断する材料にする。
     * Why not: URL形式にしない。トークンの検証に外部から取得する情報は使わず、
     * 手元の署名鍵だけで完結させるため、参照先を持つURLである必要がない。
     */
    static final String ISSUER = "mini-oms";

    /** ロールを載せるクレーム名。検証側の権限マッピングと対で意味を持つ */
    static final String ROLES_CLAIM = "roles";

    private final JwtEncoder jwtEncoder;
    private final Duration ttl;
    private final Clock clock;

    JwtAccessTokenIssuer(JwtEncoder jwtEncoder, Duration ttl, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public AccessToken issue(User user) {
        Instant issuedAt = clock.instant();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.username())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                // Why not: ロールを単一の文字列で載せない。将来ロールが増えたときに
                // 形式を変えると、発行済みトークンの解釈が変わってしまう
                .claim(ROLES_CLAIM, List.of(user.role().name()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new AccessToken(value, ttl.toSeconds());
    }
}
