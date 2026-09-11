package com.minioms.presentation.security;

import com.jayway.jsonpath.JsonPath;
import com.minioms.TestcontainersConfiguration;
import com.minioms.application.auth.PasswordHasher;
import com.minioms.application.auth.UserRepository;
import com.minioms.domain.user.Role;
import com.minioms.domain.user.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.crypto.SecretKey;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * APIの認可の仕様。
 *
 * <p>認可の線は「見るだけの人(VIEWER)」と「動かす人(OPERATOR)」の1本だけ。
 * 参照は両方に許し、受注や在庫が動く操作は OPERATOR に限る。</p>
 *
 * <p>Why not: 認証をモックで差し替えない。トークンの発行から検証、権限への変換までを
 * 実際に通さないと、クレーム名や権限の接頭辞の食い違いという最も起きやすい設定ミスを
 * 素通りさせてしまう。ログインAPIから本物のトークンを取って使う。</p>
 */
@DisplayName("APIの認可")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ApiAuthorizationTest {

    private static final String OPERATOR_PASSWORD = "operator-password";
    private static final String VIEWER_PASSWORD = "viewer-password";

    /** 存在しない受注のID。認可を通過したかどうかだけを見たい操作で使う */
    private static final long UNKNOWN_ORDER_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** アプリが実際に署名に使っている鍵。偽装トークンを本物と同じ鍵で作るために借りる */
    @Autowired
    private SecretKey jwtSigningKey;

    @BeforeEach
    void 利用者を用意する() {
        jdbcTemplate.update("DELETE FROM users");
        userRepository.save(User.of("operator", passwordHasher.hash(OPERATOR_PASSWORD), Role.OPERATOR));
        userRepository.save(User.of("viewer", passwordHasher.hash(VIEWER_PASSWORD), Role.VIEWER));
    }

    // --- 認証 ---

    @Test
    void ログインは認証なしで行える() throws Exception {
        // 認証を受ける入口まで認証必須にすると、誰もログインできなくなる
        mockMvc.perform(login("operator", OPERATOR_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void 誤ったパスワードではトークンを発行しない() throws Exception {
        mockMvc.perform(login("operator", "wrong-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void トークンなしでは受注を参照できない() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("認証が必要です"));
    }

    @Test
    void 改ざんされたトークンでは受注を参照できない() throws Exception {
        // 署名を検証しなければ、ロールを書き換えたトークンで権限を詐称できる
        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 署名が正しくても発行元が違うトークンは受け付けない() {
        // Why: iss を載せているのに検証しなければ、その主張は何も保証していないことになる。
        // 鍵が他の用途にも使われた場合に、別の発行元のトークンをそのまま受け入れてしまう
        String 別発行元のトークン = 偽装トークン("someone-else", "OPERATOR");

        org.assertj.core.api.Assertions.assertThatCode(() ->
                mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + 別発行元のトークン))
                        .andExpect(status().isUnauthorized()))
                .doesNotThrowAnyException();
    }

    @Test
    void 本物のトークンでも署名部を差し替えれば弾かれる() throws Exception {
        // ヘッダとペイロードは本物のまま、署名だけを別の値にする。
        // 署名を検証していなければ、ペイロードのロールを書き換えるだけで権限を詐称できる
        String viewerToken = bearerFor("viewer", VIEWER_PASSWORD).substring("Bearer ".length());
        String[] parts = viewerToken.split("\\.");

        mockMvc.perform(get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer %s.%s.%s".formatted(parts[0], parts[1], "tampered")))
                .andExpect(status().isUnauthorized());
    }

    // --- 参照は両方のロールに許す ---

    @Test
    void VIEWERは受注一覧を参照できる() throws Exception {
        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, bearerFor("viewer", VIEWER_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void VIEWERは在庫を参照できる() throws Exception {
        mockMvc.perform(get("/api/stocks").header(HttpHeaders.AUTHORIZATION, bearerFor("viewer", VIEWER_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void OPERATORも受注一覧を参照できる() throws Exception {
        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, bearerFor("operator", OPERATOR_PASSWORD)))
                .andExpect(status().isOk());
    }

    // --- 受注と在庫を動かす操作は OPERATOR だけ ---

    @Test
    void VIEWERは受注を確認済にできない() throws Exception {
        mockMvc.perform(changeStatus("/api/orders/1/confirmation", bearerFor("viewer", VIEWER_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("権限がありません"));
    }

    @Test
    void VIEWERは出荷指示できない() throws Exception {
        // 出荷指示は倉庫でのピッキングを起動する。参照権限しか持たない人が
        // 誤って叩けると、システムの外に取り消せない指示が出る
        mockMvc.perform(changeStatus("/api/orders/1/shipping-instruction", bearerFor("viewer", VIEWER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void VIEWERは受注をキャンセルできない() throws Exception {
        mockMvc.perform(changeStatus("/api/orders/1/cancellation", bearerFor("viewer", VIEWER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void OPERATORは更新系の操作を認可で弾かれない() throws Exception {
        // 存在しない受注なので 404 になるが、403 でないことが「認可は通った」ことを示す。
        // VIEWER の同じ操作が 403 になるのと対で読む
        mockMvc.perform(changeStatus("/api/orders/%d/confirmation".formatted(UNKNOWN_ORDER_ID),
                        bearerFor("operator", OPERATOR_PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // --- 明示していないパスは通さない ---

    @Test
    void 認可を定めていないパスは認証済みでも拒否する() throws Exception {
        // エンドポイントを追加して認可の指定を忘れても、無防備には公開されない
        mockMvc.perform(get("/whatever").header(HttpHeaders.AUTHORIZATION, bearerFor("operator", OPERATOR_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // --- ヘルパー ---

    private String bearerFor(String username, String password) throws Exception {
        String body = mockMvc.perform(login(username, password))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    /** アプリの署名鍵で、発行元だけ差し替えたトークンを作る */
    private String 偽装トークン(String issuer, String role) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("operator")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", List.of(role))
                .build();

        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String username, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password));
    }

    private static org.springframework.test.web.servlet.RequestBuilder changeStatus(String path, String bearer) {
        return post(path)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}");
    }
}
