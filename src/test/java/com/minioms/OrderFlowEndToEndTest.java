package com.minioms;

import com.jayway.jsonpath.JsonPath;
import com.minioms.application.order.ImportOrdersUseCase;
import com.minioms.application.order.ImportSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 主要フローの通し確認(E2E)。
 *
 * <p>モールからの取込・認証・認可・受注のライフサイクル・在庫引当は、それぞれ単体では
 * 仕様を満たしていても、繋いだときに噛み合わないことがある。トークンのクレーム名、
 * ステータス遷移に対する在庫の反応、冪等キーの効き方は、通しで動かして初めて確認できる。</p>
 *
 * <p>Why not: 認証やモールAPIをモックに差し替えない。層ごとのテストは既に各層にあり、
 * ここで同じことを繰り返しても意味がない。このテストが担うのは「本物同士を繋いだときに
 * 破綻しないこと」であり、差し替えた時点でその役目を果たせなくなる。</p>
 *
 * <p>Why not: ブラウザ操作(Playwright)は使わない。このシステムは REST API だけを
 * 公開しており、操作する画面が存在しない。画面の無いものをブラウザで動かしても、
 * 確認できるのは結局 HTTP のやり取りだけになる。</p>
 */
@DisplayName("主要フローの通し確認")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "mock"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class OrderFlowEndToEndTest {

    /**
     * モールAPIのモックは同一プロセス内に居るため、アプリは自分自身をHTTPで呼ぶ。
     * 接続先の設定が {@code ${server.port}} を参照している以上、ポートは起動前に
     * 確定していなければならず、ランダムポート(起動後に決まる)は使えない。
     */
    private static final int PORT = 空きポートを探す();

    /** モールBが「新規受付(01)」で返す受注。XML形式のモールから来た1件を通しの対象にする */
    private static final String MALL_B_ORDER_NUMBER = "B0000123";
    private static final String PRODUCT_CODE = "SKU-777";
    private static final int ORDERED_QUANTITY = 2;
    private static final int INITIAL_ON_HAND = 6;

    @DynamicPropertySource
    static void ポートを固定する(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ImportOrdersUseCase importOrdersUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 前回のテストの痕跡を消す() {
        // 利用者は消さない。mock プロファイルの起動時投入(DemoUserSeeder)を
        // そのまま使うことで、ログインできる状態の作られ方まで通しの対象にする
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update(
                "UPDATE stocks SET quantity_on_hand = ?, quantity_allocated = 0 WHERE product_code = ?",
                INITIAL_ON_HAND, PRODUCT_CODE);
    }

    @Test
    void 取込から出荷完了までを通しで実行できる() {
        // --- 1. 形式の異なる2つのモールから受注を取り込む ---
        ImportSummary 初回取込 = 受注を取り込む();
        assertThat(初回取込.totalImported()).isEqualTo(3);  // モールA 2件 + モールB 1件(新規受付のみ)
        assertThat(初回取込.totalFailed()).isZero();
        assertThat(初回取込.failedMallIds()).isEmpty();

        // --- 2. ログインしてトークンを得る ---
        String オペレーター = ログインする("operator", "operator-pass");
        String 閲覧者 = ログインする("viewer", "viewer-pass");

        // --- 3. 参照はどちらのロールでもできる ---
        ResponseEntity<String> 一覧 = 参照する("/api/orders?status=NEW&mallCode=MALL_B", 閲覧者);
        assertThat(一覧.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<Integer>read(一覧.getBody(), "$.totalCount")).isEqualTo(1);
        assertThat(JsonPath.<String>read(一覧.getBody(), "$.orders[0].mallOrderNumber"))
                .isEqualTo(MALL_B_ORDER_NUMBER);

        long 受注ID = JsonPath.<Integer>read(一覧.getBody(), "$.orders[0].id").longValue();
        long 版 = JsonPath.<Integer>read(一覧.getBody(), "$.orders[0].version").longValue();

        // --- 4. 参照権限しか無い人は受注を動かせない ---
        // 出荷指示は倉庫のピッキングを起動する。業務ロジックに届く前に弾かれる必要がある
        assertThat(操作する(受注ID, "shipping-instruction", 閲覧者, 版).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(在庫(閲覧者)).containsEntry("quantityAllocated", 0);

        // --- 5. 確認すると在庫が引き当てられる(実在庫は減らない) ---
        ResponseEntity<String> 確認済 = 操作する(受注ID, "confirmation", オペレーター, 版);
        assertThat(確認済.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<String>read(確認済.getBody(), "$.status")).isEqualTo("CONFIRMED");
        assertThat(在庫(閲覧者))
                .containsEntry("quantityOnHand", INITIAL_ON_HAND)
                .containsEntry("quantityAllocated", ORDERED_QUANTITY)
                .containsEntry("availableQuantity", INITIAL_ON_HAND - ORDERED_QUANTITY);

        // --- 6. 出荷指示では在庫は動かない(モノはまだ倉庫にある) ---
        ResponseEntity<String> 出荷指示済 = 操作する(受注ID, "shipping-instruction", オペレーター, 版(確認済));
        assertThat(出荷指示済.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<String>read(出荷指示済.getBody(), "$.status")).isEqualTo("SHIPPING_INSTRUCTED");
        assertThat(在庫(閲覧者))
                .containsEntry("quantityOnHand", INITIAL_ON_HAND)
                .containsEntry("quantityAllocated", ORDERED_QUANTITY);

        // --- 7. 出荷完了で引当が実在庫から落ちる ---
        ResponseEntity<String> 出荷済 = 操作する(受注ID, "shipment", オペレーター, 版(出荷指示済));
        assertThat(出荷済.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<String>read(出荷済.getBody(), "$.status")).isEqualTo("SHIPPED");
        assertThat(在庫(閲覧者))
                .containsEntry("quantityOnHand", INITIAL_ON_HAND - ORDERED_QUANTITY)
                .containsEntry("quantityAllocated", 0)
                .containsEntry("availableQuantity", INITIAL_ON_HAND - ORDERED_QUANTITY);

        // --- 8. 取込を繰り返しても二重に増えない ---
        // 取込は毎回同じ期間を遡って取得する運用のため、重複が来るのが常態
        ImportSummary 再取込 = 受注を取り込む();
        assertThat(再取込.totalImported()).isZero();
        assertThat(再取込.totalSkipped()).isEqualTo(3);
        assertThat(在庫(閲覧者)).containsEntry("quantityOnHand", INITIAL_ON_HAND - ORDERED_QUANTITY);
    }

    @Test
    void トークンを持たない要求は業務ロジックに届かない() {
        受注を取り込む();

        assertThat(restTemplate.getForEntity("/api/orders", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 画面のファイルは認証なしで取得できるがデータは入っていない() {
        // 画面は空の器で、表示するデータはすべて認証付きのAPIから取りに行く。
        // 器を開くだけで受注や在庫が見えてしまわないことを確認する
        ResponseEntity<String> page = restTemplate.getForEntity("/", String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody())
                .contains("<title>mini-oms</title>")
                .doesNotContain(MALL_B_ORDER_NUMBER)
                .doesNotContain(PRODUCT_CODE);
    }

    // --- ヘルパー ---

    private ImportSummary 受注を取り込む() {
        // 遡り期間はモック側では絞り込みに使われない。実運用と同じく重複込みで取得する
        return importOrdersUseCase.execute(OffsetDateTime.now().minusYears(1));
    }

    private String ログインする(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/auth/login", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return JsonPath.read(response.getBody(), "$.accessToken");
    }

    private ResponseEntity<String> 参照する(String path, String token) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    }

    private ResponseEntity<String> 操作する(long orderId, String operation, String token, long version) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return restTemplate.exchange(
                "/api/orders/%d/%s".formatted(orderId, operation),
                HttpMethod.POST,
                new HttpEntity<>("{\"version\":%d}".formatted(version), headers),
                String.class);
    }

    /** 更新のたびに版が上がる。次の操作には直前の応答が返した版を渡す */
    private static long 版(ResponseEntity<String> response) {
        return JsonPath.<Integer>read(response.getBody(), "$.version").longValue();
    }

    private Map<String, Object> 在庫(String token) {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/api/stocks", HttpMethod.GET, new HttpEntity<>(bearer(token)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().stream()
                .filter(stock -> PRODUCT_CODE.equals(stock.get("productCode")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("在庫が見つかりません: " + PRODUCT_CODE));
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static int 空きポートを探す() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("空きポートを確保できませんでした", e);
        }
    }
}
