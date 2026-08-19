package com.minioms.infrastructure.mall;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        return new MallAOrderClient(mallARestClient(properties.mallABaseUrl()), mallCatalog);
    }

    @Bean
    MallBOrderClient mallBOrderClient(MallProperties properties, MallCatalog mallCatalog) {
        return new MallBOrderClient(mallBRestClient(properties.mallBBaseUrl()), mallCatalog);
    }

    static RestClient mallARestClient(String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    static RestClient mallBRestClient(String baseUrl) {
        // Why not: アプリ全体のメッセージコンバータにXMLを足さない。
        // OMS自身のREST APIまでXMLを返しうる状態になるのを避け、
        // XMLの扱いはモールBのクライアント内に閉じ込める
        return RestClient.builder()
                .baseUrl(baseUrl)
                .messageConverters(converters -> converters.add(0, new MappingJackson2XmlHttpMessageConverter()))
                .build();
    }
}
