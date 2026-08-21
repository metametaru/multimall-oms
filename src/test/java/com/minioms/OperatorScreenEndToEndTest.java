package com.minioms;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.minioms.application.order.ImportOrdersUseCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.OffsetDateTime;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 画面からの通し確認(E2E)。
 *
 * <p>オペレーターが実際に行うのは「画面のボタンを押す」ことであり、
 * その結果として受注が進み在庫が動く。ボタンの出し分け・押した後の再描画・
 * 権限による表示の違いは、ブラウザで動かして初めて確認できる。</p>
 *
 * <p>{@code OrderFlowEndToEndTest} との違い:</p>
 * <ul>
 *   <li>あちらは<b>サーバー内の通し</b>。モールからの取込と冪等性を含む、
 *       画面からは起動できない経路を確認する</li>
 *   <li>こちらは<b>画面からの通し</b>。押せる操作がステータスから決まること、
 *       操作の結果が在庫の表示に反映されることを確認する</li>
 * </ul>
 *
 * <p>Why not: 画面を経由せずAPIだけを叩くテストで代用しない。それは既にあちらが
 * 担っており、ここで同じことを繰り返しても、画面とAPIの繋ぎ違い
 * (項目名の取り違え、再描画の漏れ)は捕まらない。</p>
 */
@DisplayName("画面からの通し確認")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "mock"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class OperatorScreenEndToEndTest {

    /** モールAPIのモックを自分自身へHTTPで呼ぶため、ポートは起動前に確定させる */
    private static final int PORT = 空きポートを探す();

    /** モールBから来る受注。XML形式のモールの1件を画面から進める */
    private static final String MALL_B_ORDER_NUMBER = "B0000123";
    private static final String PRODUCT_CODE = "SKU-777";
    private static final int ORDERED_QUANTITY = 2;
    private static final int INITIAL_ON_HAND = 6;

    private static Playwright playwright;
    private static Browser browser;

    @DynamicPropertySource
    static void ポートを固定する(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
    }

    @Autowired
    private ImportOrdersUseCase importOrdersUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void ブラウザを起動する() {
        playwright = Playwright.create();
        // Why not: ヘッドありで動かさない。確認したいのは画面の挙動であって描画そのものではなく、
        // ウィンドウが開くとCIでも手元でも実行の邪魔になる
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void ブラウザを閉じる() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void 受注と在庫を用意する() {
        // 利用者は消さない。mock プロファイルの起動時投入をそのまま使う
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update(
                "UPDATE stocks SET quantity_on_hand = ?, quantity_allocated = 0 WHERE product_code = ?",
                INITIAL_ON_HAND, PRODUCT_CODE);
        importOrdersUseCase.execute(OffsetDateTime.now().minusYears(1));

        // Why not: コンテキストをテスト間で使い回さない。画面はトークンをメモリにしか
        // 持たないため、タブを開き直すことがそのままログアウトになる
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void タブを閉じる() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void オペレーターは画面から受注を出荷まで進められる() {
        ログインする("operator", "operator-pass");

        Locator 受注 = 受注行();
        assertThat(ステータス(受注)).hasText("新規受付");
        在庫が(INITIAL_ON_HAND, 0, INITIAL_ON_HAND);

        // 確認 → 引当。実在庫は減らない
        操作する(受注, "確認");
        assertThat(ステータス(受注)).hasText("確認済");
        在庫が(INITIAL_ON_HAND, ORDERED_QUANTITY, INITIAL_ON_HAND - ORDERED_QUANTITY);

        // 出荷指示 → 在庫は動かない。倉庫でのピッキングを起こす操作であり、
        // これ以降はキャンセルできなくなる
        操作する(受注, "出荷指示");
        assertThat(ステータス(受注)).hasText("出荷指示済");
        assertThat(操作ボタン(受注, "キャンセル")).hasCount(0);
        在庫が(INITIAL_ON_HAND, ORDERED_QUANTITY, INITIAL_ON_HAND - ORDERED_QUANTITY);

        // 出荷完了 → 引当が実在庫から落ちる
        操作する(受注, "出荷完了");
        assertThat(ステータス(受注)).hasText("出荷完了");
        在庫が(INITIAL_ON_HAND - ORDERED_QUANTITY, 0, INITIAL_ON_HAND - ORDERED_QUANTITY);
    }

    @Test
    void 押せる操作は受注のステータスから決まる() {
        // 画面が対応表を持っていれば、遷移を追加したときにここが古いまま通ってしまう。
        // 押せるボタンがサーバーの返す一覧どおりであることを画面上で確かめる
        ログインする("operator", "operator-pass");
        Locator 受注 = 受注行();

        assertThat(操作ボタン(受注)).hasText(new String[]{"確認", "キャンセル"});

        操作する(受注, "確認");
        assertThat(操作ボタン(受注)).hasText(new String[]{"出荷指示", "キャンセル"});

        操作する(受注, "出荷指示");
        assertThat(操作ボタン(受注)).hasText(new String[]{"出荷完了"});
    }

    @Test
    void 参照権限の利用者には操作ボタンが出ない() {
        ログインする("viewer", "viewer-pass");

        Locator 受注 = 受注行();
        assertThat(ステータス(受注)).hasText("新規受付");
        assertThat(操作ボタン(受注)).hasCount(0);
        assertThat(受注.locator(".actions")).hasText("参照のみ");

        // 見えないだけでなく、在庫も動いていない
        在庫が(INITIAL_ON_HAND, 0, INITIAL_ON_HAND);
    }

    @Test
    void 資格情報が誤っていれば画面に理由が出る() {
        page.navigate(baseUrl());
        page.fill("#username", "operator");
        page.fill("#password", "wrong-password");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン")).click();

        assertThat(page.locator("#toast")).hasText("ユーザー名またはパスワードが正しくありません");
        assertThat(page.locator("#work")).isHidden();
    }

    // --- 画面操作 ---

    private void ログインする(String username, String password) {
        page.navigate(baseUrl());
        page.fill("#username", username);
        page.fill("#password", password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("ログイン")).click();

        assertThat(page.locator("#work")).isVisible();
    }

    /**
     * 対象の受注行。IDを直に書かず注文番号から引くのは、
     * 採番が実行順に依存するため
     */
    private Locator 受注行() {
        return page.locator("#orders tr").filter(new Locator.FilterOptions().setHasText(MALL_B_ORDER_NUMBER));
    }

    private void 操作する(Locator 受注, String 操作名) {
        操作ボタン(受注, 操作名).click();
        // 再描画の完了を待つ。押した操作が消えていれば描き直されている
        assertThat(操作ボタン(受注, 操作名)).hasCount(0);
    }

    private static Locator 操作ボタン(Locator 受注) {
        return 受注.locator(".actions button");
    }

    private static Locator 操作ボタン(Locator 受注, String 操作名) {
        return 受注.locator(".actions button", new Locator.LocatorOptions().setHasText(操作名));
    }

    private static Locator ステータス(Locator 受注) {
        return 受注.locator(".status");
    }

    private void 在庫が(int 実在庫, int 引当済, int 引当可能) {
        Locator row = page.locator("#stocks tr[data-product-code='%s']".formatted(PRODUCT_CODE));
        assertThat(row.locator(".on-hand")).hasText(String.valueOf(実在庫));
        assertThat(row.locator(".allocated")).hasText(String.valueOf(引当済));
        assertThat(row.locator(".available")).hasText(String.valueOf(引当可能));
    }

    private static String baseUrl() {
        return "http://localhost:" + PORT + "/";
    }

    private static int 空きポートを探す() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("空きポートを確保できませんでした", e);
        }
    }
}
