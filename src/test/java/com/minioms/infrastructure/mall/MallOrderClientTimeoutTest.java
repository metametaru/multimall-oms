package com.minioms.infrastructure.mall;

import com.sun.net.httpserver.HttpServer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 応答が返らないモールを打ち切る仕様。
 *
 * <p>取込は単一のスケジューラが全モールを順に呼ぶため、1 モールが応答を返さないまま
 * 接続を掴み続けると、後続モールの取込も次回の実行も始まらない。打ち切りは
 * 「そのモールを諦める」ためではなく「他モールを進める」ために要る。</p>
 *
 * <p>Why not: モックモールAPI(Spring)を使わない。ここで確かめたいのは
 * 「繋がるのに返ってこない相手」に対する挙動であり、アプリを起動せずに素のHTTPサーバを
 * 立てた方が、待ち時間も再現性も制御できる。</p>
 */
@DisplayName("モールAPIのタイムアウト")
class MallOrderClientTimeoutTest {

    private static final MallProperties.Timeouts 短いタイムアウト =
            new MallProperties.Timeouts(Duration.ofMillis(200), Duration.ofMillis(300));

    /** 打ち切りが効いていればこれよりはるかに早く戻る。効いていなければ到達しない */
    private static final Duration 打ち切りの上限 = Duration.ofSeconds(5);

    /**
     * モール側が応答を返さないまま掴み続ける時間。テスト終了時に解放する。
     * 打ち切りが壊れたときにCIが待たされる時間でもあるので、判定に要る長さまでに留める。
     */
    private static final Duration 無応答を続ける時間 = Duration.ofSeconds(10);

    private static final OffsetDateTime SINCE = OffsetDateTime.parse("2026-08-01T00:00:00+09:00");

    private final CountDownLatch 終了 = new CountDownLatch(1);
    private HttpServer 無応答モール;

    @BeforeEach
    void 応答を返さないモールを立てる() throws IOException {
        無応答モール = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // 接続は受け付けるが応答は書かない。「相手が落ちている」ではなく
        // 「繋がるのに返ってこない」状態を作る
        無応答モール.createContext("/", exchange -> {
            awaitUninterruptibly(終了, 無応答を続ける時間);
            exchange.close();
        });
        無応答モール.start();
    }

    @AfterEach
    void 片付ける() {
        // countDown が先。ハンドラは既定のディスパッチャスレッドで動くので、
        // 解放しないまま stop すると応答待ちのスレッドが残る
        終了.countDown();
        無応答モール.stop(0);
    }

    @Test
    void 応答を返さないモールAは打ち切られる() {
        MallAOrderClient client = new MallAOrderClient(
                MallClientConfig.mallARestClient(無応答モールのURL(), 短いタイムアウト),
                mock(MallCatalog.class));

        assertThat(打ち切られるまでの時間(() -> client.fetchOrdersPlacedSince(SINCE)))
                .isLessThan(打ち切りの上限);
    }

    @Test
    void 応答を返さないモールBは打ち切られる() {
        MallBOrderClient client = new MallBOrderClient(
                MallClientConfig.mallBRestClient(無応答モールのURL(), 短いタイムアウト),
                mock(MallCatalog.class));

        assertThat(打ち切られるまでの時間(() -> client.fetchOrdersPlacedSince(SINCE)))
                .isLessThan(打ち切りの上限);
    }

    /**
     * タイムアウトの既定値は設定ファイルではなく型が持つ。
     * 設定を書き忘れた環境で無制限に戻ることが、この仕組みの唯一の抜け道になるため。
     *
     * <p>Why not: 「0 より大きい」だけを見ない。それだと既定を 1ms に変えても通ってしまう。
     * 実際の値を置いて、変更が MallProperties.Timeouts の根拠(全モールの一巡が
     * 取込間隔に収まる)を読み直す合図になるようにする。</p>
     */
    @Test
    void 設定を書かなくてもタイムアウトは入っている() {
        MallProperties bound = bind(Map.of());

        assertThat(bound.timeout().connect()).isEqualTo(Duration.ofSeconds(3));
        assertThat(bound.timeout().read()).isEqualTo(Duration.ofSeconds(10));
    }

    /**
     * 片方だけ設定する運用は現実に起きる(読取だけ延ばしたい等)。
     * その時に書かなかった側が無制限に戻らないことを確かめる。
     */
    @Test
    void 片方だけ設定しても書かなかった側は既定のままになる() {
        MallProperties bound = bind(Map.of("minioms.mall.timeout.read", "30s"));

        assertThat(bound.timeout().read()).isEqualTo(Duration.ofSeconds(30));
        assertThat(bound.timeout().connect()).isEqualTo(Duration.ofSeconds(3));
    }

    private static MallProperties bind(Map<String, String> 設定) {
        Map<String, Object> source = new HashMap<>(設定);
        source.put("minioms.mall.mall-a-base-url", "http://mall-a.invalid");
        source.put("minioms.mall.mall-b-base-url", "http://mall-b.invalid");

        return new Binder(new MapConfigurationPropertySource(source))
                .bind("minioms.mall", MallProperties.class)
                .get();
    }

    private String 無応答モールのURL() {
        return "http://127.0.0.1:" + 無応答モール.getAddress().getPort();
    }

    private Duration 打ち切られるまでの時間(ThrowingCallable 取得) {
        long 開始 = System.nanoTime();
        assertThatThrownBy(取得)
                // ImportOrdersUseCase は RuntimeException を捕えて「取得失敗モール」に落とす。
                // ここが検査例外やエラーで上がると取込全体が止まるため、型そのものが仕様になる
                .isInstanceOf(RuntimeException.class);
        return Duration.ofNanos(System.nanoTime() - 開始);
    }

    private static void awaitUninterruptibly(CountDownLatch latch, Duration timeout) {
        try {
            latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
