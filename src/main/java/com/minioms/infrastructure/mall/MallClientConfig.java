package com.minioms.infrastructure.mall;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * モールクライアントの組み立て。
 *
 * <p>Why not: モールクライアント側に {@code @Component} を付けない。
 * モールごとにベースURLとレスポンス形式が異なり、必要な RestClient も別物になるため、
 * 組み立ての知識をこの設定クラスに集約する。</p>
 */
@Configuration
@EnableConfigurationProperties(MallProperties.class)
class MallClientConfig {

    @Bean
    MallAOrderClient mallAOrderClient(MallProperties properties, MallCatalog mallCatalog) {
        return new MallAOrderClient(
                mallARestClient(properties.mallABaseUrl(), properties.timeout()), mallCatalog);
    }

    @Bean
    MallBOrderClient mallBOrderClient(MallProperties properties, MallCatalog mallCatalog) {
        return new MallBOrderClient(
                mallBRestClient(properties.mallBBaseUrl(), properties.timeout()), mallCatalog);
    }

    static RestClient mallARestClient(String baseUrl, MallProperties.Timeouts timeout) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(timeout))
                .build();
    }

    static RestClient mallBRestClient(String baseUrl, MallProperties.Timeouts timeout) {
        // Why not: アプリ全体のメッセージコンバータにXMLを足さない。
        // OMS自身のREST APIまでXMLを返しうる状態になるのを避け、
        // XMLの扱いはモールBのクライアント内に閉じ込める
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(timeout))
                .messageConverters(converters -> converters.add(0, new MappingJackson2XmlHttpMessageConverter()))
                .build();
    }

    /**
     * Why not: RestClient の既定のリクエストファクトリに任せない。既定はタイムアウトを
     * 持たず、応答を返さないモールが 1 つあると取込が単一スケジューラごと止まる。
     *
     * <p>Why not: {@code detect()} で実装を自動選択しない。クラスパスの中身が変わるだけで
     * HTTP クライアントの実装が入れ替わり、タイムアウトの効き方まで変わってしまう。
     * どの実装で何秒待つのかは、取込が止まるかどうかを決める設定なので明示する。</p>
     *
     * <p>読取タイムアウトは JDK の HttpClient のリクエストタイムアウトとして効く。
     * 応答が返り始めるまでを計るので、ヘッダだけ返して本文を止める相手には効かない。
     * そこまで塞ぐには呼び出し側に総量の上限が要るが、まず「繋がらない」「返ってこない」
     * という実際に起きる形を止める。</p>
     */
    private static ClientHttpRequestFactory requestFactory(MallProperties.Timeouts timeout) {
        return ClientHttpRequestFactoryBuilder.jdk()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(timeout.connect())
                        .withReadTimeout(timeout.read()));
    }
}
